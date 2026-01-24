package com.bitark.wal.adapter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import log.LogEntry;
import log.LogEntryHandler;
import com.bitark.wal.WalEngine;
import com.bitark.wal.WalReader.WalReader_V1;
import com.bitark.wal.WalWriter.WalWriter_V2;
import com.bitark.wal.checkpoint.WalCheckpoint;
import com.bitark.wal.config.WalConfig;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class GroupCommitWalEngine  implements WalEngine{


    private final WalWriter_V2 writer;
    private final WalReader_V1 reader;
    private final WalConfig config;

    public GroupCommitWalEngine(WalConfig config) throws IOException {
        this.config = config;
        this.writer = WalWriter_V2.init(config);
        this.reader = new WalReader_V1();
    }

    @Override
    public CompletableFuture<Boolean> append(LogEntry entry) {
        return writer.append(entry);
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
}
