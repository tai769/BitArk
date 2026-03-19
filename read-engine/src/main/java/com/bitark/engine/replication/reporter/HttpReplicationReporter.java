package com.bitark.engine.replication.reporter;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.engine.replication.config.ReplicationConfig;
import org.springframework.web.client.RestTemplate;

public class HttpReplicationReporter implements ReplicationReporter{

    private final RestTemplate restTemplate;
    private final ReplicationConfig replicationConfig;

    public HttpReplicationReporter(RestTemplate restTemplate, ReplicationConfig replicationConfig) {
        this.restTemplate = restTemplate;
        this.replicationConfig = replicationConfig;
    }
    @Override
    public void reportStartup(Long globalLsn) {
        String masterUrl = replicationConfig.getMasterUrl();
        if (masterUrl == null || masterUrl.isBlank()) {
            return;
        }
        ReplicationAck ack = new ReplicationAck();
        ack.setSlaveUrl(replicationConfig.getSelfUrl());
        ack.setGlobalLsn(globalLsn);
        restTemplate.postForObject(masterUrl + "/internal/register", ack, String.class);
    }
}
