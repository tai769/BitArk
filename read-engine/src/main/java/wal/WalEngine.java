package wal;

import java.util.concurrent.CompletableFuture;

public interface WalEngine {

    CompletableFuture<Boolean> append(LogEntry entry);

    Long replay(LogEntryHandler handler) throws Exception;

}
