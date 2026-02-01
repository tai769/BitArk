package com.bitark.engine.service.command;

import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.lsn.LsnPosition;

public interface ReadCommandService {
    void read(Long userId, Long msgId) throws Exception;
    void readFromMaster(Long userId, Long msgId) throws Exception;
    LsnPosition applyReplication(ReplicationRequest req) throws Exception;
}
