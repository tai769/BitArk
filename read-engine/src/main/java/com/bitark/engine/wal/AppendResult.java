package com.bitark.engine.wal;

import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class AppendResult {
    private final long leaderLsn;
    private final int epoch;
    private final WalPosition position;
}
