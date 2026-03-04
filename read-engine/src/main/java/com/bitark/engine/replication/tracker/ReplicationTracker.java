package com.bitark.engine.replication.tracker;

import com.bitark.commons.dto.HeartBeatDTO;
import com.bitark.commons.lsn.LsnPosition;

public interface ReplicationTracker {
    void registerAck(String slaveId, LsnPosition lsn, boolean needsFullSync);
    LsnPosition getMinAckLsn();

    void onHeartbeat(String slaveId, LsnPosition lsn);
    int evictExpired();
}
