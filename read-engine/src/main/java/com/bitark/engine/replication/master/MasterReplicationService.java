package com.bitark.engine.replication.master;

import com.bitark.commons.dto.HeartBeatDTO;
import com.bitark.commons.dto.ReplicationAck;

public interface MasterReplicationService {
    String register(ReplicationAck ack);

    void onHeartbeat(HeartBeatDTO dto);
}
