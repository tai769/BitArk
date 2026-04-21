package com.bitark.engine.replication.slave;

import com.bitark.commons.dto.FetchResponse;
import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;

public interface SlaveReplicationService {
    /**
     * 旧 Push 模式遗留入口。
     * 当前主链路已切到 Pull，仅作为历史代码保留。
     */
    ReplicationAck sync(ReplicationRequest req) throws Exception;


    void applyFetchBatch(FetchResponse resp) throws Exception;

    void handNeedFullSync();
}
