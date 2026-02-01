package com.bitark.engine.replication.tracker;

import com.bitark.commons.lsn.LsnPosition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlaveState {
    LsnPosition ackLsn;
    long lastHeartbeatMs;
}
