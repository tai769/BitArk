package com.bitark.engine.replication.pull;

import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.slave.SlaveReplicationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ScheduledExecutorService;


@ConditionalOnProperty(
        prefix = "replication",
        name = "pull-enabled",
        havingValue = "true"
)
@Configuration
public class SlavePullSchedulerConfig {

    @Bean(initMethod = "start")
    public SlavePullScheduler slavePullScheduler(ReplicationConfig replicationConfig,
                                                 ReplicationProgressStore replicationProgressStore,
                                                 RestTemplate restTemplate,
                                                 SlaveReplicationService slaveReplicationService,
                                                 @Qualifier("pullScheduler") ScheduledExecutorService executorService) {
        return new SlavePullScheduler(replicationConfig, replicationProgressStore, restTemplate, slaveReplicationService, executorService);
    }




}
