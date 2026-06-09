package com.bitark.engine.wal.store;

import com.bitark.commons.log.WalRecord;
import com.bitark.commons.log.WalRecordHandler;
import com.bitark.commons.wal.WalCheckpoint;
import com.bitark.engine.WalReader.FileReadBatch;
import com.bitark.engine.WalReader.WalReader;
import com.bitark.engine.WalWriter.WalWriter_V2;
import com.bitark.engine.wal.WalConfig;
import com.bitark.engine.wal.WalIndex;
import com.bitark.engine.wal.WalPosition;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Arrays;

@Slf4j
@Data
public class FileWalStore implements WalStore{

    private final WalWriter_V2 writer;
    private final WalReader reader;
    private final WalConfig config;
    private volatile long earliestRetainedLsn;


    public FileWalStore(WalConfig config, WalIndex walIndex) throws Exception {
        this.config = config;
        this.writer = WalWriter_V2.init(config,walIndex);
        this.reader = new WalReader();
        this.earliestRetainedLsn = calcEarliestRetainedLsn();
    }


    @Override
    public WriterResult append(WalRecord record) {
        return writer.append(record).join();
    }

    @Override
    public void replay(WalRecordHandler handler) throws Exception {
        File[] files = listAndSortSegments();
        for (File file : files){
            reader.replay(file.getAbsolutePath(),handler);
        }
    }

    @Override
    public FileReadBatch readFrom(WalPosition position, int maxBytes) throws Exception {
        String path = buildFilePath(position.getSegmentIndex());
        return reader.readBatch(path,position.getOffset(),maxBytes);
    }

    @Override
    public WalCheckpoint currentCheckPoint() throws Exception {
        return writer.currentCheckpoint();
    }

    @Override
    public void gcOldSegment(WalCheckpoint checkpoint) throws Exception {
        String baseName = config.getWalFileName();
        File[] files = listAndSortSegments();
        int deletedCount = 0;
        for(File f : files){
            int idx = parseIndex(f.getName());
            if (idx < checkpoint.getSegmentIndex()){
                boolean deleted = f.delete();
                if (deleted){
                    log.info("gc deleted old segment: {}", f.getName());
                    deletedCount++;
                }else {
                    log.warn("Failed to delete: {}", f.getName());
                }
            }else {
                log.info("GC keep segment: {}", f.getName());
                break;
            }
        }
        log.info("gc completed. Deleted {} old segments", deletedCount);
        earliestRetainedLsn = calcEarliestRetainedLsn();
    }

    @Override
    public long earliestRetainedLsn() {
        return earliestRetainedLsn;
    }


    private int parseIndex(String name){
        String baseName = config.getWalFileName();
        String suffix = name.substring((baseName + ".").length());
        return Integer.parseInt(suffix);
    }

    private File[] listAndSortSegments(){
        String dir = config.getWalDir();
        String baseName = config.getWalFileName();

        File folder = new File(dir);

        if (!folder.exists()){
            return new File[0];
        }

        File[] files = folder.listFiles(
                file -> file.getName().startsWith(baseName + ".")
        );
        if (files == null || files.length == 0){
            return  new File[0];
        }
        Arrays.sort(files , (f1,f2) ->
                Integer.compare(parseIndex(f1.getName()),
                parseIndex(f2.getName())));
        return files;

    }

    private String buildFilePath(long segmentIndex){
        return config.getWalDir()
                +File.separator
                + config.getWalFileName()
                + "."
                + segmentIndex;
    }



    private long calcEarliestRetainedLsn(){
        File[] files = listAndSortSegments();
        if (files.length == 0) {
            return 0L;
        }
       try {
           WalRecord first =
                   reader.readFirstRecord(files[0].getAbsolutePath());
           if (first == null){
               return 0L;
           }
           return first.getLeaderLsn();
       }catch (Exception e){
           throw new RuntimeException("Failed to calculate earliestRetainedLsn", e)
       }
    }
}
