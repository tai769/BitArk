package com.bitark.adapter.config;

import com.bitark.engine.config.ReplicationConfig;
import com.bitark.engine.replication.ReplicationProgressStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class replicationProgressStoreConfig {

    @Bean
    public ReplicationProgressStore replicationProgressStore(ReplicationConfig replicationConfig) {
        return new ReplicationProgressStore(Paths.get(replicationConfig.getProgressPath()));
    }

}
