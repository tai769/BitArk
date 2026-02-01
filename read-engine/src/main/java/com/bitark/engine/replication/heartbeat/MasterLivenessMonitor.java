package com.bitark.engine.replication.heartbeat;

import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import com.bitark.infrastructure.thread.ThreadUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MasterLivenessMonitor {

    private final ReplicationTracker tracker;
    private final ReplicationConfig config;
    private ScheduledExecutorService scheduler;

    public MasterLivenessMonitor(ReplicationTracker tracker, ReplicationConfig config) {
        this.tracker = tracker;
        this.config = config;
    }

    @PostConstruct
    public void start(){
        scheduler = ThreadUtils.newSingleThreadScheduledExecutor("eliminate", false);
        scheduler.scheduleAtFixedRate(() -> {
            int removed = tracker.evictExpired();
            if (removed > 0){
                log.info("evict dead slaves: {}", removed);
            }
        }, config.getHeartbeatIntervalMs(), config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

}
