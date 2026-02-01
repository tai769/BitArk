package com.bitark.engine.replication.progress;

import com.bitark.engine.replication.config.ReplicationConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class ReplicationProgressStoreConfig {

    @Bean
    public ReplicationProgressStore replicationProgressStore(ReplicationConfig replicationConfig) {
        return new ReplicationProgressStore(Paths.get(replicationConfig.getProgressPath()));
    }

}
