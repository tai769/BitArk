package com.bitark.engine.service.command;

import com.bitark.commons.dto.ReplicationRequest;

public interface ReadCommandService {
    void read(Long userId, Long msgId) throws Exception;
    void readFromMaster(Long userId, Long msgId) throws Exception;
    Long applyReplication(ReplicationRequest req) throws Exception;
}
