package com.bitark.engine.replication.tracker;

import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.wal.WalEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReplicationTrackerConfig {

    @Bean
    public ReplicationTracker replicationTracker(ReplicationConfig config, WalEngine walEngine) {
        return new ReplicationTrackerImpl(config.getHeartbeatTimeoutMs(), () -> {
            try {
                return walEngine.currCheckpoint();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, config.getMaxLagBytes(), config.getIsrJoinStreak());
    }
}
