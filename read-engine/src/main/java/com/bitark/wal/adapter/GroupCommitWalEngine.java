package com.bitark.wal.adapter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.bitark.log.LogEntry;
import com.bitark.log.LogEntryHandler;
import com.bitark.engine.WalEngine;
import com.bitark.wal.WalReader.WalReader_V1;
import com.bitark.wal.WalWriter.WalWriter_V2;
import com.bitark.wal.config.WalConfig;

public class GroupCommitWalEngine  implements WalEngine{


    private final WalWriter_V2 writer;
    private final WalReader_V1 reader;
    private final WalConfig config;

    public GroupCommitWalEngine(WalConfig config) throws IOException {
        this.config = config;
        this.writer = WalWriter_V2.init(config.getWalPath());
        this.reader = new WalReader_V1();
    }

    @Override
    public CompletableFuture<Boolean> append(LogEntry entry) {
        return writer.append(entry);
    }

    @Override
    public Long replay(LogEntryHandler handler) throws Exception {
        return reader.replay(config.getWalPath(), handler);
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }

}
