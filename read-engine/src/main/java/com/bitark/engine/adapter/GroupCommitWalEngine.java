package com.bitark.engine.adapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.bitark.engine.WalReader.FileReadBatch;
import com.bitark.engine.WalReader.WalReader;
import com.bitark.engine.WalWriter.WalWriter_V2;
import com.bitark.engine.wal.WalConfig;
import com.bitark.commons.log.LogEntry;
import com.bitark.commons.log.LogEntryHandler;
import com.bitark.commons.wal.WalCheckpoint;
import com.bitark.engine.wal.WalEngine;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class GroupCommitWalEngine  implements WalEngine{


    private final WalWriter_V2 writer;
    private final WalReader reader;
    private final WalConfig config;
    private volatile long earliestRetainedLsn;

    public GroupCommitWalEngine(WalConfig config) throws IOException {
        this.config = config;
        this.writer = WalWriter_V2.init(config);
        this.reader = new WalReader();
        this.earliestRetainedLsn = calcEarliestRetainedLsn();
    }

    @Override
    public Long append(LogEntry entry) {
        try{
            //1. 调用底层的appenWalCheckpointd
            CompletableFuture<Long>
                    future = writer.append(entry);

            //2. 关键调用.join同步等待磁盘写入返回lsn
            return future.join();
        }catch(Exception e){
            log.error("WAL append error", e);
            throw new RuntimeException("Failed to append WAL", e);
        }
    }

    @Override
    public Long replay(LogEntryHandler handler) throws Exception {

          //1. 列出目录下的文件
        File[] files = listAndSortSegments();

        long lastOffset = 0L;
        for (File f : files) {
            lastOffset = reader.replay(f.getAbsolutePath(), handler);
        }
        return lastOffset;
    }

    




    private int parseIndex(String name, String baseName) {
       String suffix = name.substring((baseName + ".").length());
       try {
           return Integer.parseInt(suffix);
       } catch (NumberFormatException e) {
           return 0; //异常情况下当0处理.
       }


       
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }

    @Override
    public WalCheckpoint currCheckpoint() throws Exception {
        return writer.currentCheckpoint();    
    }

    @Override
    public Long replayFrom(WalCheckpoint checkpoint, LogEntryHandler handler) throws Exception {
        String baseName = config.getWalFileName();
        File[] files = listAndSortSegments();

        long lastOffset = 0L;
        for(File f : files){
            int idx = parseIndex(f.getName(), baseName);
            String path = f.getAbsolutePath();

            if (idx < checkpoint.getSegmentIndex()) {
                continue; //旧文件全部跳过
            }else if (idx == checkpoint.getSegmentIndex()) {
                lastOffset = reader.replay(path, checkpoint.getSegmentOffset(), handler);
            }else {
                lastOffset = reader.replay(path, handler); // 从头读取
            }
        }
        return lastOffset;

    }

    @Override
    public void gcOldSegment(WalCheckpoint checkpoint) throws Exception {
        String baseName = config.getWalFileName();
        File[] files = listAndSortSegments();
        
        int deletedCount = 0;
        for(File f : files){
            int idx = parseIndex(f.getName(), baseName);

            // 只删除严格小于 checkpoint的文件(当前正在写的和未来的都保留)
            if (idx < checkpoint.getSegmentIndex()) {
                boolean deleted = f.delete();
                if (deleted) {
                    log.info("🗑️  GC deleted old segment: {}", f.getName());
                    deletedCount++;
                }else{
                    log.warn("⚠️  Failed to delete: {}", f.getName());
                }
                
            }else {

                log.info("✅ GC keep segment: {}", f.getName());
                break;
            }
        }
        log.info("✅ GC completed. Deleted {} old segment(s)", deletedCount);
        earliestRetainedLsn = calcEarliestRetainedLsn();
    }

    /**
     * 转换全局LSN为WalCheckpoint
     * @param globalLsn
     * @return
     */
    @Override
    public WalCheckpoint toCheckpoint(Long globalLsn) throws Exception {

        return new WalCheckpoint((int) (globalLsn/ config.getMaxFileSizeBytes()), globalLsn% config.getMaxFileSizeBytes());
    }

    @Override
    public long earliestRetainedLsn() {
        return earliestRetainedLsn;
    }

    @Override
    public WalReadBatch readBatch(Long fromLsn, Integer maxBytes) throws Exception {
        //1. 把全局LSN变成checkPont
        WalCheckpoint start = toCheckpoint(fromLsn);
        String baseName = config.getWalFileName();
        List<LogEntry> allEntries = new ArrayList<>();
        int finalSegmentIndex = start.getSegmentIndex();
        long finalOffset = start.getSegmentOffset();
        int totalBytes = 0;
        //2. 拿到所有的segment文件
        File[] files = listAndSortSegments();
        if (files.length == 0) {
            return new WalReadBatch(new ArrayList<>(), fromLsn);
        }
        for (File f : files){
            int idx = parseIndex(f.getName(), baseName);
            if ( idx < finalSegmentIndex){
                continue;
            }

            // 决定当前文件从哪里读
            long startOffset;
            if (idx == finalSegmentIndex){
                startOffset = finalOffset;
            }else {
                startOffset = 0L;
            }
            int remainBytes = maxBytes - totalBytes;
            if (remainBytes < LogEntry.ENTRY_SIZE){
                break;
            }
            FileReadBatch fileBatch = reader.readBatch(f.getAbsolutePath(), startOffset, remainBytes);
            // 计算这次还能读多少字节
            allEntries.addAll(fileBatch.getEntries());
            totalBytes += fileBatch.getEntries().size()*LogEntry.ENTRY_SIZE;
            finalSegmentIndex = idx;
            finalOffset = fileBatch.getNextOffset();

            if (totalBytes >= maxBytes){
                break;
            }
            if (!fileBatch.isReachFileEnd()){
                break;
            }

        }
        long nextLsn = finalSegmentIndex*config.getMaxFileSizeBytes() + finalOffset;
        return new WalReadBatch(allEntries, nextLsn);
    }

    private File[] listAndSortSegments(){
        String walDir = config.getWalDir();
        String baseName = config.getWalFileName();
        File[] files = new File(walDir).listFiles( f ->
                f.getName().startsWith(baseName + "."));

        if (files == null || files.length == 0){
            log.warn("⚠️  No segment files found in {}", walDir);
            return new File[0];
        }
        Arrays.sort(files, (f1, f2) -> {
            int i1 = parseIndex(f1.getName(), baseName);
            int i2 = parseIndex(f2.getName(), baseName);
            return Integer.compare(i1, i2);
        });

        return files;
    }

    private long calcEarliestRetainedLsn() {
        File[] files = listAndSortSegments();
        if (files.length == 0) {
            return 0L;
        }
        int minIndex = parseIndex(files[0].getName(), config.getWalFileName());
        return minIndex * config.getMaxFileSizeBytes();
    }
}
