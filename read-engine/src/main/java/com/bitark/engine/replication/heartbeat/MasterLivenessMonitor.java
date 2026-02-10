package com.bitark.engine.replication.heartbeat;

import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MasterLivenessMonitor {

    private final ReplicationTracker tracker;
    private final ReplicationConfig config;
    private final ScheduledExecutorService livenessScheduler;

    public MasterLivenessMonitor(ReplicationTracker tracker, ReplicationConfig config,ScheduledExecutorService livenessScheduler ) {
        this.tracker = tracker;
        this.livenessScheduler = livenessScheduler;
        this.config = config;
    }

    @PostConstruct
    public void start(){
        livenessScheduler.scheduleAtFixedRate(() -> {
            int removed = tracker.evictExpired();
            if (removed > 0){
                log.info("evict dead slaves: {}", removed);
            }
        }, config.getHeartbeatIntervalMs(), config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
    }


}
