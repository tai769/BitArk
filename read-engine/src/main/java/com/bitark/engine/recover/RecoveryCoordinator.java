package com.bitark.engine.recover;

import com.bitark.commons.wal.WalCheckpoint;
import com.bitark.engine.ReadStatusEngine;

public interface RecoveryCoordinator {
    void recover(ReadStatusEngine engine) throws Exception;

    WalCheckpoint snapshot(ReadStatusEngine engine) throws Exception;
}
