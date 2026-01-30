package com.bitark.engine.config;



import com.bitark.engine.replication.ReplicationProgressStore;
import com.bitark.engine.replication.ReplicationTracker;
import com.bitark.engine.service.ReadServiceImpl;
import com.bitark.engine.wal.WalEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.ExecutorService;


@Configuration
public class ServiceConfig {

    @Bean
    public ReadServiceImpl readService(WalEngine walEngine, RecoveryConfig recoveryConfig, RestTemplate restTemplate, ReplicationConfig replicationConfig, ExecutorService executorService, ReplicationProgressStore progessStore, ReplicationTracker replicationTracker) throws Exception {
        return new ReadServiceImpl(walEngine, recoveryConfig, restTemplate, replicationConfig, executorService, progessStore, replicationTracker);
    }
}
