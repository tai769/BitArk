package com.bitark.engine.replication.fullsync;

import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.recover.FullSnapshot;
import com.bitark.engine.recover.SnapshotManager;
import com.bitark.engine.wal.WalEngine;

/**
 * FullSyncService 的默认实现。
 *
 * <p>职责：
 * 1. 通过 SnapshotManager 将 ReadStatusEngine 导出为 snapshotBytes
 * 2. 通过 WalEngine 获取这份快照对应的 WAL 进度
 * 3. 组装引擎内部的 FullSnapshot</p>
 *
 * <p>它不负责：
 * 1. 把 FullSnapshot 转成 HTTP 响应
 * 2. Slave 端如何覆盖本地状态
 * 3. Full Sync 失败后的重试策略</p>
 */
public class FullSyncServiceImpl implements FullSyncService{

    private final ReadStatusEngine engine;
    private final WalEngine walEngine;
    private final SnapshotManager snapshotManager;

    public FullSyncServiceImpl(ReadStatusEngine engine, WalEngine walEngine, SnapshotManager snapshotManager) {
        this.engine = engine;
        this.walEngine = walEngine;
        this.snapshotManager = snapshotManager;
    }

    /**
     * 生成 Master 当前完整状态快照。
     *
     * <p>当前方法的核心难点不是“如何得到 byte[]”，
     * 而是如何保证 snapshotBytes 和 snapshotLsn 描述的是同一个时刻。</p>
     */
    @Override
    public FullSnapshot takeFullSnapshot() thrZows Exception {
        return null;
    }
}
