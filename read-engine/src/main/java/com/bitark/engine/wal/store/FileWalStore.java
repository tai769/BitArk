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

@Data
public class FileWalStore implements WalStore{

    private final WalWriter_V2 writer;
    private final WalReader reader;
    private final WalConfig config;


    public FileWalStore(WalConfig config, WalIndex walIndex){
        this.config = config;
        this.writer = WalWriter_V2.init(config,walIndex);
        this.reader = new WalReader();
    }


    @Override
    public WriterResult append(WalRecord record) {
        return writer.append(record).join();
    }

    @Override
    public void replay(WalRecordHandler handler) throws Exception {
        //遍历wal segment reader.replay
    }

    @Override
    public FileReadBatch readFrom(WalPosition position, int maxBytes) throws Exception {
        // 用 position.segmentIndex 拼文件路径
        // 用 position.offset 调 reader.readBatch(path, offset,maxBytes)
        return null;
    }

    @Override
    public WalCheckpoint currentCheckPoint() throws Exception {
        return writer.currentCheckpoint();
    }

    @Override
    public void gcOldSegment(WalCheckpoint checkpoint) throws Exception {
        // 复用原 GroupCommitWalEngine.gcOldSegment 的逻辑
    }
}
