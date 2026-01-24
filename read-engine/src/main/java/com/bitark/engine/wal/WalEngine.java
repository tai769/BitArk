package com.bitark.engine.wal;

import com.bitark.engine.checkpoint.WalCheckpoint;
import com.bitark.log.LogEntry;
import com.bitark.log.LogEntryHandler;

import java.util.concurrent.CompletableFuture;


public interface WalEngine {

    CompletableFuture<Boolean> append(LogEntry entry);

    Long replay(LogEntryHandler handler) throws Exception;

    void close() throws Exception;

    WalCheckpoint currCheckpoint() throws Exception;

    Long replayFrom(WalCheckpoint checkpoint, LogEntryHandler handler) throws Exception;


    void gcOldSegment(WalCheckpoint checkpoint) throws Exception;

}
