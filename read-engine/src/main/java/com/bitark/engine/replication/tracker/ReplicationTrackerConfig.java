package com.bitark.engine.replication.tracker;

import com.bitark.engine.replication.config.ReplicationConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReplicationTrackerConfig {

    @Bean
    public ReplicationTracker replicationTracker(ReplicationConfig config) {
        return new ReplicationTrackerImpl( config.getHeartbeatTimeoutMs());
    }
}
