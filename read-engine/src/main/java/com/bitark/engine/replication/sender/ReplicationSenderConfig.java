package com.bitark.engine.replication.sender;

import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutorService;

@Component
public class ReplicationSenderConfig {
    @Bean
    public ReplicationSender replicationSender(RestTemplate restTemplate, ReplicationConfig replicationConfig, ReplicationTracker replicationTracker
    , ExecutorService executorService) {
        return new HttpReplicationSender(restTemplate, replicationConfig, replicationTracker, executorService);
    }
}
