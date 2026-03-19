package com.bitark.engine.replication.tracker;

import lombok.*;

@ToString
@AllArgsConstructor
@Getter
public class SlaveState {
    private final Long globalLsn;
    private final long lastHeartbeatMs;

    private final ReplicaStatus status;

    private final Integer healthyStreak;

    private final boolean needsFullSync;
}
