package com.bitark.engine;

import com.bitark.log.LogEntry;
import com.bitark.log.LogEntryHandler;
import com.bitark.wal.checkpoint.WalCheckpoint;

import java.util.concurrent.CompletableFuture;

public interface WalEngine {

    CompletableFuture<Boolean> append(LogEntry entry);

    Long replay(LogEntryHandler handler) throws Exception;

    void close() throws Exception;

    WalCheckpoint currCheckpoint() throws Exception;


}
