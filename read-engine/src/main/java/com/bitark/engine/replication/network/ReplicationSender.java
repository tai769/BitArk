package com.bitark.engine.replication.network;

import com.bitark.commons.wal.WalCheckpoint;



public interface ReplicationSender {

    void sendRead(Long userId, Long msgId, WalCheckpoint lsn);
}
