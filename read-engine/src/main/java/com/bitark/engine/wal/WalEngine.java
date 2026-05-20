package com.bitark.engine.wal;

import com.bitark.commons.log.WalRecord;
import com.bitark.commons.log.WalRecordHandler;
import com.bitark.commons.wal.WalCheckpoint;
import com.bitark.engine.adapter.WalReadBatch;


public interface WalEngine {

    Long append(WalRecord record);

    Long replay(WalRecordHandler handler) throws Exception;

    void close() throws Exception;

    WalCheckpoint currCheckpoint() throws Exception;

    Long replayFrom(WalCheckpoint checkpoint, WalRecordHandler handler) throws Exception;

    void gcOldSegment(WalCheckpoint checkpoint) throws Exception;

    WalCheckpoint toCheckpoint(Long lsn) throws Exception;

    long earliestRetainedLsn();

    WalReadBatch 3(Long fromLsn, Integer maxBytes) throws Exception;

}
