package com.bitark.engine.replication.reporter;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.lsn.LsnPosition;
import com.bitark.engine.config.ReplicationConfig;
import org.springframework.web.client.RestTemplate;

public class HttpReplicationReporter implements ReplicationReporter{

    private final RestTemplate restTemplate;
    private final ReplicationConfig replicationConfig;

    public HttpReplicationReporter(RestTemplate restTemplate, ReplicationConfig replicationConfig) {
        this.restTemplate = restTemplate;
        this.replicationConfig = replicationConfig;
    }
    @Override
    public void reportStartup(LsnPosition lsn) {
        String masterUrl = replicationConfig.getMasterUrl();
        if (masterUrl == null && masterUrl.isBlank()) {
            return;
        }
        ReplicationAck ack = new ReplicationAck();
        ack.setSlaveUrl(replicationConfig.getSelfUrl());
        ack.setAckSegmentIndex(lsn.getSegmentIndex());
        ack.setAckOffset(lsn.getOffset());
        restTemplate.postForObject(masterUrl + "/internal/register", ack, String.class);
    }
}
