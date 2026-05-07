package com.bitark.engine.recover;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class SnapshotManagerConfig {
    @Bean
    public SnapshotManager snapshotManager(RecoveryConfig recoveryConfig) {
        return new SnapshotManager(Paths.get(recoveryConfig.getSnapshotPath()));
    }
}
