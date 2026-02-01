package com.bitark.engine.replication.slave;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;

public interface SlaveReplicationService {
    ReplicationAck sync(ReplicationRequest req) throws Exception;
}
