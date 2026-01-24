package com.bitark.adapter.config;



import com.bitark.engine.config.RecoveryConfig;
import com.bitark.engine.config.ReplicationConfig;
import com.bitark.engine.service.ReadServiceImpl;
import com.bitark.engine.wal.WalEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.ExecutorService;


@Configuration
public class ServiceConfig {

    @Bean
    public ReadServiceImpl readService(WalEngine walEngine, RecoveryConfig recoveryConfig, RestTemplate restTemplate, ReplicationConfig replicationConfig, ExecutorService executorService) throws Exception {
        return new ReadServiceImpl(walEngine, recoveryConfig, restTemplate, replicationConfig, executorService);
    }
}
