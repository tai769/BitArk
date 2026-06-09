package com.bitark.engine.wal;

import com.bitark.engine.wal.engine.DefaultLogEngine;
import com.bitark.engine.wal.store.FileWalStore;
import com.bitark.engine.wal.store.WalStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogEngineConfig {

    @Bean
    public WalIndex walIndex() {
        return new WalIndex();
    }

    @Bean
    public LogSequencer logSequencer() {
        return new LogSequencer();
    }

    @Bean
    public WalStore walStore(WalConfig walConfig, WalIndex walIndex) throws Exception {
        return new FileWalStore(walConfig, walIndex);
    }

    @Bean
    public LogEngine logEngine(LogSequencer sequencer, WalStore walStore, WalIndex walIndex) {
        return new DefaultLogEngine(sequencer, walStore, walIndex);
    }
}
