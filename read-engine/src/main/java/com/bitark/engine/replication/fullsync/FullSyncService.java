package com.bitark.engine.replication.fullsync;

import com.bitark.engine.recover.FullSnapshot;

/**
 * Full Sync 快照生成服务。
 *
 * <p>职责：
 * 1. 在 Master 本地生成用于 Slave 全量恢复的快照结果
 * 2. 将完整状态 snapshotBytes 和对应 snapshotLsn 绑定成 FullSnapshot</p>
 *
 * <p>它不负责：
 * 1. HTTP 请求处理
 * 2. Slave 本地应用快照
 * 3. 增量 WAL 拉取
 * 4. 判断某个 Slave 是否需要 Full Sync</p>
 */
public interface FullSyncService {
    /**
     * 生成一份用于 Full Sync 的完整快照。
     *
     * <p>返回值必须同时包含状态数据和该状态对应的 WAL 进度。
     * 后续实现的重点是保证这两个值来自同一个逻辑时刻。</p>
     */
    FullSnapshot takeFullSnapshot() throws Exception;
}
