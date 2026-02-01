package com.bitark.engine.replication.sender;

import com.bitark.commons.wal.WalCheckpoint;



public interface ReplicationSender {

    void sendRead(Long userId, Long msgId, WalCheckpoint lsn);
}
