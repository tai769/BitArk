package com.bitark.engine.replication.slave;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.lsn.LsnPosition;
import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.service.command.ReadCommandService;


public class SlaveReplicationServiceImpl implements SlaveReplicationService{

    private final ReadCommandService readCommandService;
    private final ReplicationConfig replicationConfig;

    public SlaveReplicationServiceImpl(ReadCommandService readCommandService, ReplicationConfig replicationConfig) {
        this.readCommandService = readCommandService;
        this.replicationConfig = replicationConfig;
    }
    @Override
    public ReplicationAck sync(ReplicationRequest req) throws Exception {
        LsnPosition lsn = readCommandService.applyReplication(req);
        ReplicationAck ack = new ReplicationAck();
        ack.setSlaveUrl(replicationConfig.getSelfUrl());   // 只能由本机填写
        ack.setAckSegmentIndex(lsn.getSegmentIndex());
        ack.setAckOffset(lsn.getOffset());
        return ack;
    }
}
