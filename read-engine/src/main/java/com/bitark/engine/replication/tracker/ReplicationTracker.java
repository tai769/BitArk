package com.bitark.engine.replication.tracker;

/**
 *   - 它只做三件事：
 *       - 记录每个 slave 的复制进度
 *       - 根据心跳和 lag 维护 ISR 状态
 *       - 给 GC 提供最慢 ISR 水位
 *   - 它不负责：
 *       - 从 WAL 读 batch
 *       - 组装 FetchResponse
 *       - 执行 Full Sync 传输
 */

public interface ReplicationTracker {
    void registerAck(String slaveId, Long globalLsn );
    Long getMinIsrAckLsn();

    void onHeartbeat(String slaveId, Long globalLsn);
    int evictExpired();

    ;
}
