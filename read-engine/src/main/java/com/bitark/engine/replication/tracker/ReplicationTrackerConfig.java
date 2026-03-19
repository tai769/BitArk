package com.bitark.engine.replication.tracker;

import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.commons.wal.WalCheckpoint;
import com.bitark.engine.wal.WalConfig;
import com.bitark.engine.wal.WalEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReplicationTrackerConfig {

    @Bean
    public ReplicationTracker replicationTracker(ReplicationConfig config, WalEngine walEngine, WalConfig walConfig) {
        return new ReplicationTrackerImpl(config.getHeartbeatTimeoutMs(), () -> {
            try {
                WalCheckpoint cp = walEngine.currCheckpoint();
                long segmentOffset = cp.getSegmentOffset();
                long segmentIndex = cp.getSegmentIndex();
                return segmentIndex * walConfig.getMaxFileSizeBytes() + segmentOffset;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, config.getMaxLagBytes(), config.getIsrJoinStreak());
    }
}
