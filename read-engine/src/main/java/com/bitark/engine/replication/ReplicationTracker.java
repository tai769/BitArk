package com.bitark.engine.replication;

import com.bitark.commons.lsn.LsnPosition;

public interface ReplicationTracker {
    void registerAck(String slaveId, LsnPosition lsn);
    LsnPosition getMinAckLsn();
}
