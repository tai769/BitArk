package com.bitark.engine.replication.fullsync;

import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.recover.SnapshotManager;
import com.bitark.engine.wal.WalEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Full Sync 模块装配配置。
 *
 * <p>职责：
 * 1. 将 FullSyncServiceImpl 创建为 Spring Bean
 * 2. 明确 FullSyncServiceImpl 需要的三个底层能力：
 *    ReadStatusEngine、WalEngine、SnapshotManager</p>
 *
 * <p>它不负责：
 * 1. 生成快照
 * 2. 处理 HTTP 请求
 * 3. 判断节点角色</p>
 */
@Configuration
public class FullSyncServiceConfig {
    /**
     * 创建 FullSyncService。
     *
     * <p>依赖来源：
     * SnapshotManager 负责快照二进制格式；
     * ReadStatusEngine 提供当前内存状态；
     * WalEngine 提供 WAL 进度边界。</p>
     */
    @Bean
    public FullSyncService fullSyncService(SnapshotManager snapshotManager,
                                           ReadStatusEngine engine,
                                           WalEngine walEngine) {
        return new FullSyncServiceImpl(engine, walEngine, snapshotManager);
    }
}
