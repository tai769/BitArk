package com.bitark.engine.replication;

import com.bitark.commons.dto.HeartBeatDTO;
import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;

public interface ReplicationService {
    ReplicationAck sync(ReplicationRequest req) throws Exception; //从哭应用

    String register(ReplicationAck ack); //主库登记


}
