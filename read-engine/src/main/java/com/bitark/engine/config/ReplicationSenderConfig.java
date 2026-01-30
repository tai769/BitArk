package com.bitark.engine.config;

import com.bitark.engine.replication.ReplicationTracker;
import com.bitark.engine.replication.network.HttpReplicationSender;
import com.bitark.engine.replication.network.ReplicationSender;
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
