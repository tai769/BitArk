package com.bitark.engine.service.facade;

import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.lsn.LsnPosition;

public interface ReadService {
    void read(Long userId, Long msgId) throws Exception;


    void recover() throws Exception;

    boolean isRead(Long userId, Long msgId);

    void readFromMaster (Long userId, Long msgId)throws Exception;


    void snapshot() throws Exception;


}
