package com.bitark.engine.replication.tracker;

public interface ReplicationTracker {
    void registerAck(String slaveId, Long globalLsn );
    Long getMinIsrAckLsn();

    void onHeartbeat(String slaveId, Long globalLsn);
    int evictExpired();
}
