package com.bitark.engine.wal.store;

import com.bitark.commons.log.WalRecord;
import com.bitark.commons.log.WalRecordHandler;
import com.bitark.commons.wal.WalCheckpoint;
import com.bitark.engine.WalReader.FileReadBatch;
import com.bitark.engine.wal.WalPosition;

public interface WalStore {

    WriterResult append(WalRecord record);

    void replay(WalRecordHandler handler) throws Exception;

    FileReadBatch readFrom(WalPosition position, int maxBytes) throws  Exception;

    WalCheckpoint currentCheckPoint() throws  Exception;

    void gcOldSegment(WalCheckpoint checkpoint) throws  Exception;
}
