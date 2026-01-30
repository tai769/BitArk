package com.bitark.engine.config;

import com.bitark.engine.replication.ReplicationTracker;
import com.bitark.engine.replication.ReplicationTrackerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReplicationTrackerConfig {

    @Bean
    public ReplicationTracker replicationTracker() {
        return new ReplicationTrackerImpl();
    }
}
