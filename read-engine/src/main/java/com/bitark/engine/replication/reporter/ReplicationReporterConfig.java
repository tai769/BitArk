package com.bitark.engine.replication.reporter;

import com.bitark.engine.replication.config.ReplicationConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ReplicationReporterConfig {

    @Bean
    public ReplicationReporter replicationReporter(RestTemplate restTemplate, ReplicationConfig replicationConfig) {
        return new HttpReplicationReporter(restTemplate, replicationConfig);
    }

}
