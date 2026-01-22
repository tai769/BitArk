package com.bitark.wal;

import log.LogEntry;
import log.LogEntryHandler;
import com.bitark.wal.checkpoint.WalCheckpoint;

import java.util.concurrent.CompletableFuture;

public interface WalEngine {

    CompletableFuture<Boolean> append(LogEntry entry);

    Long replay(LogEntryHandler handler) throws Exception;

    void close() throws Exception;

    WalCheckpoint currCheckpoint() throws Exception;

    Long replayFrom(WalCheckpoint checkpoint, LogEntryHandler handler) throws Exception;


    void gcOldSegment(WalCheckpoint checkpoint) throws Exception;

}
