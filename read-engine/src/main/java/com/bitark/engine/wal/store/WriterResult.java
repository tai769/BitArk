package com.bitark.engine.wal.store;

import com.bitark.commons.log.WalRecord;
import com.bitark.engine.wal.WalPosition;
import lombok.Data;

@Data
public class WriterResult {
    private final WalRecord record;

    private final WalPosition position;
}
