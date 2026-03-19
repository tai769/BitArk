package com.bitark.engine.replication.sender;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.tracker.ReplicationTracker;
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
    public void sendRead(Long userId, Long msgId, Long lsn) {
        executorService.submit( () -> {
            try{
                ReplicationRequest request = new ReplicationRequest();
                request.setUserId(userId);
                request.setMsgId(msgId);
                request.setGlobalLsn(lsn);

                ReplicationAck ack = restTemplate.postForObject(
                        replicationConfig.getSlaveUrl(), request, ReplicationAck.class);
                if (ack != null && ack.getSlaveUrl() != null && !ack.getSlaveUrl().isBlank()){
                    replicationTracker.registerAck(ack.getSlaveUrl(), ack.getGlobalLsn());
                    log.info("Replication ack received from {} for {}", ack.getSlaveUrl(), ack.getGlobalLsn());
                }
            }catch (Exception e){
                log.error("Replication error", e);
            }
        });
    }
}
