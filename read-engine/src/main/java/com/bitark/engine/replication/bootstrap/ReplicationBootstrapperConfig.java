package com.bitark.engine.replication.bootstrap;

import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.reporter.ReplicationReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ReplicationBootstrapperConfig {

    @Bean
    public ReplicationBootstrapper replicationBootstrapper(
            ReplicationProgressStore progressStore,
            ReplicationReporter reporter) {
        return new ReplicationBootstrapper(progressStore, reporter);
    }
}
