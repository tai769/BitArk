package com.bitark.engine.replication;

import com.bitark.commons.dto.HeartBeatDTO;
import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.lsn.LsnPosition;
import com.bitark.engine.service.ReadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ReplicationServiceImpl implements ReplicationService {

    private final ConcurrentHashMap<String, LsnPosition> ackMap = new ConcurrentHashMap<>();

    private final ReadService readService;
    private final ReplicationTracker tracker;


    public ReplicationServiceImpl(ReadService readService, ReplicationTracker tracker) {
        this.readService = readService;
        this.tracker = tracker;
    }
    @Override
    public ReplicationAck sync(ReplicationRequest req) throws Exception {
        return readService.applyReplication(req);
    }

    @Override
    public String register(ReplicationAck ack) {
        tracker.registerAck(ack.getSlaveUrl(), ack.toLsnPosition());
        log.info("📢 Slave Registered: {} at {}", ack.getSlaveUrl(), ack.toLsnPosition());
        return "ok";
    }

 
}
