package com.bitark.engine.replication.tracker;


import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
public class ReplicationTrackerImpl implements ReplicationTracker {

    private final long heartBeatTimeOutMs;
    private final ConcurrentHashMap<String, SlaveState> ackMap = new ConcurrentHashMap<>();
    private final Supplier<Long> masterGlobalLsnSupplier;



    private long lag;
    private int isrJoinStreak;

    public ReplicationTrackerImpl(long heartBeatTimeOutMs, Supplier<Long> masterGlobalLsnSupplier
        , long lag, int isrHealthyStreak) {
        this.heartBeatTimeOutMs = heartBeatTimeOutMs;
        this.masterGlobalLsnSupplier = masterGlobalLsnSupplier;
        this.lag = lag;
        this.isrJoinStreak = isrHealthyStreak;
    }

    @Override
    public void registerAck(String slaveId, Long globalLsn) {
        if (slaveId == null || slaveId.isBlank() || globalLsn == null) {
            return;
        }
        ackMap.compute(slaveId, (key, existing) -> {
            if (existing == null) {
                return new SlaveState(globalLsn, System.currentTimeMillis(), ReplicaStatus.OBSERVER, 0, false);
            }
                return new SlaveState((globalLsn > existing.getGlobalLsn()) ? globalLsn : existing.getGlobalLsn(), existing.getLastHeartbeatMs(), existing.getStatus(), existing.getHealthyStreak(), false);
        });
    }

    @Override
    public Long getMinIsrAckLsn() {
        evictExpired();  // 堵住窗口：先清理超时节点
        if (ackMap.isEmpty()) {
            return null;
        }
        long minLsn = -1;
        for (SlaveState slaveState : ackMap.values()) {
            if (!slaveState.getStatus().equals(ReplicaStatus.ISR)) {
                continue;
            }
            if (minLsn == -1 || slaveState.getGlobalLsn() < minLsn) {
                minLsn = slaveState.getGlobalLsn();
            }
        }
        return minLsn;
    }

    @Override
    public void onHeartbeat(String slaveId, Long globalLsn) {
        if (slaveId == null || slaveId.isBlank() || globalLsn == null) {
            log.error("Invalid heartbeat: slaveId: {}, lsn: {}", slaveId, globalLsn);
            return;
        }
        Long masterGlobalLsn = masterGlobalLsnSupplier.get();
        if (masterGlobalLsn == null) {
            log.warn("Master globalLsn is null, skip heartbeat for {}", slaveId);
            return;
        }
        ackMap.compute(slaveId, (id, old) -> {
            if (old == null) {
                log.error("Slave not registered: {}", slaveId);
                return null;
            }
            if (masterGlobalLsn - globalLsn > lag) {

                return new SlaveState(globalLsn, System.currentTimeMillis(), ReplicaStatus.OUT_OF_SYNC, 0, false);
            } else {
                if (old.getStatus() != ReplicaStatus.ISR && old.getHealthyStreak()+1 < isrJoinStreak) {

                        return new SlaveState(globalLsn, System.currentTimeMillis(), old.getStatus(), old.getHealthyStreak() + 1, false);
                } else {
                    return new SlaveState(globalLsn, System.currentTimeMillis(), ReplicaStatus.ISR, old.getHealthyStreak() + 1, false);
                }
            }
        });
    }

    @Override
    public int evictExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Map.Entry<String, SlaveState> entry : ackMap.entrySet()) {
            SlaveState snapshot = entry.getValue();
            if (now - snapshot.getLastHeartbeatMs() > heartBeatTimeOutMs) {
                if (ackMap.remove(entry.getKey(), snapshot)) {
                    removed++;
                }
            }
        }
        return removed;
    }
}
