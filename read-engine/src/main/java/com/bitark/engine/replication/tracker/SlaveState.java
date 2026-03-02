package com.bitark.engine.replication.tracker;

import com.bitark.commons.lsn.LsnPosition;
import lombok.*;

@ToString
@AllArgsConstructor
@Getter
public class SlaveState {
    private final LsnPosition ackLsn;
    private final long lastHeartbeatMs;

    private final ReplicaStatus status;
    private final int healthyStreak;
    private final boolean needsFullSync;
}
