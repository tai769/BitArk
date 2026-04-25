package com.bitark.engine.replication.master;

import com.bitark.commons.dto.*;

/**
 * Master 侧复制服务。
 *
 * <p>职责：
 * 1. 维护 Slave 注册和心跳上报
 * 2. 响应 Slave 的 Pull 增量拉取
 * 3. 响应 Slave 的 Full Sync 全量同步请求</p>
 *
 * <p>它不负责：
 * 1. HTTP 路由
 * 2. Slave 本地落地
 * 3. 定时调度</p>
 */
public interface MasterReplicationService {
    /**
     * Slave 首次启动或恢复后向 Master 报备当前进度。
     */
    String register(ReplicationAck ack);

    /**
     * Slave 周期性上报存活状态和当前复制进度。
     */
    void onHeartbeat(HeartBeatDTO dto);

    /**
     * Pull 模式下的增量拉取接口。
     * Slave 给出 fromLsn，Master 返回从该位置开始的一批 WAL 变更。
     */
    FetchResponse fetch(FetchRequest  req) throws Exception;

    /**
     * Full Sync 全量同步接口。
     * 当 Slave 的 fromLsn 已经早于 Master 最早保留 WAL 时，Slave 通过该接口拉完整状态快照。
     */
    FullSyncResponse fullSync(FullSyncRequest req) throws Exception;
}
