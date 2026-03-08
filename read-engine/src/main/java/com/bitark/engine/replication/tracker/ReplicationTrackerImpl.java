package com.bitark.engine.replication.tracker;

import com.bitark.commons.lsn.LsnPosition;
import com.bitark.commons.wal.WalCheckpoint;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
public class ReplicationTrackerImpl implements ReplicationTracker {

    private final Long heartBeatTimeOutMs;
    private final ConcurrentHashMap<String, SlaveState> ackMap = new ConcurrentHashMap<>();
    private final Supplier<WalCheckpoint> walCheckpointSupplier;

    private final int Lag = 100;

    public ReplicationTrackerImpl(Long heartBeatTimeOutMs, Supplier<WalCheckpoint> walCheckpointSupplier) {
        this.heartBeatTimeOutMs = heartBeatTimeOutMs;
        this.walCheckpointSupplier = walCheckpointSupplier;
    }

    @Override
    public void registerAck(String slaveId, LsnPosition lsn) {
        if (slaveId == null || slaveId.isBlank() || lsn == null) {
            return;
        }
        ackMap.compute(slaveId, (key, existing) -> {
            if (existing == null || lsn.compareTo(existing.getAckLsn()) > 0) {
                return new SlaveState(lsn, System.currentTimeMillis(), ReplicaStatus.OBSERVER, 0,false);
            }
            return existing;
        });
    }

    @Override
    public LsnPosition getMinAckLsn() {
        if (ackMap.isEmpty()) {
            return null;
        }
        LsnPosition minLsn = null;
        for (SlaveState slaveState : ackMap.values()) {
            if (System.currentTimeMillis() - slaveState.getLastHeartbeatMs() > heartBeatTimeOutMs
                    || !slaveState.getStatus().equals(ReplicaStatus.ISR)) {
                continue;
            }
            if (minLsn == null || slaveState.getAckLsn().compareTo(minLsn) < 0) {
                minLsn = slaveState.getAckLsn();
            }
        }
        return minLsn;
    }

    @Override
    public void onHeartbeat(String slaveId, LsnPosition lsn) {
        if (slaveId == null || slaveId.isBlank() || lsn == null) {
            log.error("Invalid heartbeat: slaveId: {}, lsn: {}", slaveId, lsn);
            return;
        }
        long segmentOffset = walCheckpointSupplier.get().getSegmentOffset();
        ackMap.compute(slaveId, (id, old) -> {
            if (old == null) {
                log.error("Slave not registered: {}", slaveId);
                return null;
            }
            if (segmentOffset - lsn.getOffset() > Lag) {

                return new SlaveState(lsn, System.currentTimeMillis(), ReplicaStatus.OUT_OF_SYNC, 0, false);
            } else {
                if (old.getStatus() != ReplicaStatus.ISR && old.getHealthyStreak()+1 < 3) {

                        return new SlaveState(lsn, System.currentTimeMillis(), old.getStatus(), old.getHealthyStreak() + 1, false);
                } else {
                    return new SlaveState(lsn, System.currentTimeMillis(), ReplicaStatus.ISR, old.getHealthyStreak() + 1, false);
                }
            }
        });
    }

    @Override
    public int evictExpired() {
        long now = System.currentTimeMillis();
        AtomicInteger removed = new AtomicInteger(0);
        for (Map.Entry<String, SlaveState> entry : ackMap.entrySet()) {
            SlaveState snapshot = entry.getValue();
            if (now - snapshot.getLastHeartbeatMs() > heartBeatTimeOutMs) {
                if (ackMap.remove(entry.getKey(), snapshot)) {
                    removed.incrementAndGet();
                }
            }
        }
        return removed.get();
    }
}
