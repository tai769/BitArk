package com.bitark.engine.config;

import com.bitark.engine.replication.ReplicationService;
import com.bitark.engine.replication.ReplicationServiceImpl;
import com.bitark.engine.replication.ReplicationTracker;
import com.bitark.engine.service.ReadService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReplicationServiceConfig {

    @Bean
    public ReplicationService replicationService(ReadService readService, ReplicationTracker tracker) {
        return new ReplicationServiceImpl(readService, tracker);
    }
}
