package com.bitark.engine.replication.heartbeat;


import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HeartbeatConfig {


    @Bean
    public SlaveHeartbeatScheduler slaveHeartbeatScheduler(
            ReplicationConfig config,
            ReplicationProgressStore store,
            RestTemplate restTemplate) {
        return new SlaveHeartbeatScheduler(config, store, restTemplate);
    }

    @Bean
    public MasterLivenessMonitor masterLivenessMonitor(
            ReplicationTracker tracker,
            ReplicationConfig config) {
        return new MasterLivenessMonitor(tracker, config);
    }
}
