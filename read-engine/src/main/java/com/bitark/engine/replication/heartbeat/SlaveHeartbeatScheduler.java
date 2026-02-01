package com.bitark.engine.replication.heartbeat;

import com.bitark.commons.dto.HeartBeatDTO;
import com.bitark.commons.lsn.LsnPosition;
import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.infrastructure.thread.ThreadUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SlaveHeartbeatScheduler {
    private final ReplicationConfig config;
    private final ReplicationProgressStore store;
    private final RestTemplate restTemplate;

    private ScheduledExecutorService scheduler;
    public SlaveHeartbeatScheduler(ReplicationConfig config, ReplicationProgressStore store, RestTemplate restTemplate) {
        this.config = config;
        this.store = store;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        if (config.getMasterUrl() == null || config.getMasterUrl().isBlank()) {
            return; // 不是 slave，就不发心跳
        }
        scheduler = ThreadUtils.newSingleThreadScheduledExecutor("Heart",false);
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {

        try{
            LsnPosition lsn = store.load();
            HeartBeatDTO dto = new HeartBeatDTO();
            dto.setLsnPosition(lsn);
            dto.setTimestampMs(System.currentTimeMillis());
            restTemplate.postForObject(config.getMasterUrl(), dto, Void.class);
        }catch (Exception e){
            log.error("send heartbeat error", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
