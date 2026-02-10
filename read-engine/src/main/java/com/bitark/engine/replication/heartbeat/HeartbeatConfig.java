package com.bitark.engine.replication.heartbeat;

import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class HeartbeatConfig {

    @Bean
    public SlaveHeartbeatScheduler slaveHeartbeatScheduler(
            ReplicationConfig config,
            ReplicationProgressStore store,
            RestTemplate restTemplate,
            @Qualifier("heartbeatScheduler") ScheduledExecutorService scheduler) {
        return new SlaveHeartbeatScheduler(config, store, restTemplate, scheduler);
    }

    @Bean
    public MasterLivenessMonitor masterLivenessMonitor(
            ReplicationTracker tracker,
            ReplicationConfig config,
            @Qualifier("livenessScheduler") ScheduledExecutorService livenessScheduler) {
        return new MasterLivenessMonitor(tracker, config, livenessScheduler);
    }
}
