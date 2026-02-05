package com.bitark.engine.replication.tracker;

import com.bitark.commons.dto.HeartBeatDTO;
import com.bitark.commons.lsn.LsnPosition;

import java.util.concurrent.ConcurrentHashMap;

public class ReplicationTrackerImpl implements ReplicationTracker {


    private final Long heartBeatTimeOutMs;
    private final ConcurrentHashMap<String, SlaveState> ackMap = new ConcurrentHashMap<>();


    public ReplicationTrackerImpl(Long heartBeatTimeOutMs) {
        this.heartBeatTimeOutMs = heartBeatTimeOutMs;
    }

    @Override
    public void registerAck(String slaveId, LsnPosition lsn) {
        if (slaveId == null || slaveId.isBlank() || lsn == null){
            return;
        }
        ackMap.put(slaveId, new SlaveState(lsn, System.currentTimeMillis()));
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
        SlaveState slaveState = ackMap.computeIfAbsent(
                slaveId, k -> new SlaveState(lsn, System.currentTimeMillis())
        );
        // 更新从节点状态：更新确认的LSN和心跳时间
        slaveState.setAckLsn(lsn);
        slaveState.setLastHeartbeatMs(System.currentTimeMillis());
    }


    @Override
    public int evictExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (String slaveId : ackMap.keySet()) {
            SlaveState slaveState = ackMap.get(slaveId);
            if (now - slaveState.getLastHeartbeatMs() > heartBeatTimeOutMs) {
                ackMap.remove(slaveId);
                removed++;
            }
        }

        return removed;
    }
}
