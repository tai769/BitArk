package com.bitark.engine.wal;

import com.bitark.commons.log.LogEntry;
import com.bitark.commons.log.LogEntryHandler;
import com.bitark.commons.wal.WalCheckpoint;



public interface WalEngine {

    WalCheckpoint append(LogEntry entry);

    Long replay(LogEntryHandler handler) throws Exception;

    void close() throws Exception;

    WalCheckpoint currCheckpoint() throws Exception;

    Long replayFrom(WalCheckpoint checkpoint, LogEntryHandler handler) throws Exception;


    void gcOldSegment(WalCheckpoint checkpoint) throws Exception;

}
