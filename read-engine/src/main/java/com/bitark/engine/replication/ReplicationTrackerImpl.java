package com.bitark.engine.replication;

import com.bitark.commons.lsn.LsnPosition;

import java.util.concurrent.ConcurrentHashMap;

public class ReplicationTrackerImpl implements ReplicationTracker {

    private final ConcurrentHashMap<String, LsnPosition> ackMap = new ConcurrentHashMap<>();
    @Override
    public void registerAck(String slaveId, LsnPosition lsn) {
        if (slaveId == null || slaveId.isBlank() || lsn == null){
            return;
        }
        ackMap.put(slaveId, lsn);
    }

    @Override
    public LsnPosition getMinAckLsn() {
        if (ackMap.isEmpty()){
            return null;
        }
        LsnPosition minLsn = null;
        for (LsnPosition lsn : ackMap.values()){
            if (minLsn == null || lsn.compareTo(minLsn) < 0){
                minLsn = lsn;
            }
        }
        return minLsn;
    }
}
