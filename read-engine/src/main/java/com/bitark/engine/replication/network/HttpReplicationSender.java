package com.bitark.engine.replication.network;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.lsn.LsnPosition;
import com.bitark.commons.wal.WalCheckpoint;
import com.bitark.engine.config.ReplicationConfig;
import com.bitark.engine.replication.ReplicationTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutorService;

@Slf4j
public class HttpReplicationSender implements ReplicationSender{


    private final RestTemplate restTemplate;
    private final ReplicationConfig replicationConfig;
    private final ExecutorService executorService;
    private final ReplicationTracker replicationTracker;

    public HttpReplicationSender(RestTemplate restTemplate, ReplicationConfig replicationConfig,  ReplicationTracker replicationTracker, ExecutorService executorService) {
        this.restTemplate = restTemplate;
        this.replicationConfig = replicationConfig;
        this.executorService = executorService;
        this.replicationTracker = replicationTracker;
    }

    @Override
    public void sendRead(Long userId, Long msgId, WalCheckpoint lsn) {
        executorService.submit( () -> {
            try{
                ReplicationRequest request = new ReplicationRequest();
                request.setUserId(userId);
                request.setMsgId(msgId);
                request.setSegmentIndex(lsn.getSegmentIndex());
                request.setOffset(lsn.getSegmentOffset());

                ReplicationAck ack = restTemplate.postForObject(
                        replicationConfig.getSlaveUrl(), request, ReplicationAck.class);
                if (ack != null && ack.getSlaveUrl() != null && !ack.getSlaveUrl().isBlank()){
                    replicationTracker.registerAck(ack.getSlaveUrl(), new LsnPosition(ack.getAckSegmentIndex(), ack.getAckOffset()));
                    log.info("Replication ack received from {} for {}", ack.getSlaveUrl(), ack.getAckSegmentIndex());
                }
            }catch (Exception e){
                log.error("Replication error", e);
            }
        });
    }
}
