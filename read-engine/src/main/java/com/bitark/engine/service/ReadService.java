package com.bitark.engine.service;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.lsn.LsnPosition;
import com.bitark.commons.wal.WalCheckpoint;

import java.util.concurrent.ConcurrentHashMap;

public interface ReadService {
    void read(Long userId, Long msgId) throws Exception;


    void recover() throws Exception;

    boolean isRead(Long userId, Long msgId);

    void readFromMaster (Long userId, Long msgId)throws Exception;


    void snapshot() throws Exception;


    ConcurrentHashMap<String, LsnPosition> getSlaveAckMap();

    ReplicationAck applyReplication(ReplicationRequest req) throws Exception;
}
