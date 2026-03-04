package com.bitark.engine.replication.tracker;


import com.bitark.commons.lsn.LsnPosition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplicationTrackerImpl implements ReplicationTracker {


    private final Long heartBeatTimeOutMs;
    private final ConcurrentHashMap<String, SlaveState> ackMap = new ConcurrentHashMap<>();


    public ReplicationTrackerImpl(Long heartBeatTimeOutMs) {
        this.heartBeatTimeOutMs = heartBeatTimeOutMs;
    }

    @Override
    public void registerAck(String slaveId, LsnPosition lsn, boolean needsFullSync) {
        if (slaveId == null || slaveId.isBlank() || lsn == null){
            return;
        }
        ackMap.compute(slaveId, (key, existing) -> {
            if (existing == null || lsn.compareTo(existing.getAckLsn()) > 0) {
                return new SlaveState(lsn, System.currentTimeMillis(), ReplicaStatus.OBSERVER , 0, needsFullSync);
            }
            return existing;
        });
    }

    @Override
    public LsnPosition getMinAckLsn() {
        if (ackMap.isEmpty()){
            return null;
        }
        LsnPosition minLsn = null;
        for (SlaveState slaveState : ackMap.values()){
            if (System.currentTimeMillis() - slaveState.getLastHeartbeatMs() > heartBeatTimeOutMs ){
                continue;
            }
            if (minLsn == null || slaveState.getAckLsn().compareTo(minLsn) < 0){
                minLsn = slaveState.getAckLsn();
            }
        }
        return minLsn;
    }


    @Override
    public void onHeartbeat(String slaveId, LsnPosition lsn) {
        if (slaveId == null || slaveId.isBlank() || lsn == null){
            return;
        }
        ackMap.compute(slaveId, (id , old) -> {
            if (old == null){
                return new SlaveState(lsn, System.currentTimeMillis(), ReplicaStatus.OBSERVER, 0, true);
            }

            return new SlaveState(lsn, System.currentTimeMillis(), old.getStatus(), old.getHealthyStreak(), old.isNeedsFullSync());


        });
    }


    @Override
    public int evictExpired() {
        long now = System.currentTimeMillis();
        AtomicInteger removed = new AtomicInteger(0);
        for (Map.Entry<String, SlaveState> entry : ackMap.entrySet()){
            SlaveState snapshot = entry.getValue();
            if (now - snapshot.getLastHeartbeatMs() > heartBeatTimeOutMs){
                if (ackMap.remove(entry.getKey(), snapshot)){
                    removed.incrementAndGet();
                }
            }
        }
        return removed.get();
    }
}
