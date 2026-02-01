package com.bitark.engine.replication.master;

import com.bitark.commons.dto.HeartBeatDTO;
import com.bitark.commons.dto.ReplicationAck;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MasterReplicationServiceImpl implements MasterReplicationService{

    private ReplicationTracker tracker;

    public MasterReplicationServiceImpl(ReplicationTracker tracker) {
        this.tracker = tracker;
    }


    @Override
    public String register(ReplicationAck ack) {
        tracker.registerAck(ack.getSlaveUrl(), ack.toLsnPosition());
        log.info("📢 Slave Registered: {} at {}", ack.getSlaveUrl(), ack.toLsnPosition());
        return "ok";
    }

    @Override
    public void onHeartbeat(HeartBeatDTO dto) {
        if (dto == null || dto.getSlaveUrl() == null){
            return;
        }
        tracker.onHeartbeat(dto.getSlaveUrl(), dto.getLsnPosition());
    }
}
