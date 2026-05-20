package com.bitark.engine.wal;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * WAL 内存索引。
 *
 * <p>WalIndex 维护 leaderLsn 到本机物理位置 WalPosition 的映射。
 *
 * <p>leaderLsn 是复制层、状态机层使用的逻辑坐标；
 * WalPosition 是 WAL 存储层内部使用的物理坐标。
 *
 * <p>第一版只做内存索引，启动时通过扫描 WAL 文件重建。
 * 后续如果 WAL 很大，可以再演进为磁盘稀疏索引。
 */
public class WalIndex {
    private final ConcurrentSkipListMap<Long,WalPosition> index =
            new ConcurrentSkipListMap<>();

    /**
     * 注册一条 leaderLsn 对应的物理位置。
     */
    public void put(long leaderLsn, WalPosition position){
        if (position == null){
            throw new IllegalArgumentException("position is null");
        }
        index.put(leaderLsn, position);
    }

    /**
     * 精确查找某条 leaderLsn 的物理位置。
     */
    public WalPosition get(long leaderLsn){
        return index.get(leaderLsn);
    }

    /**
     * 查找大于等于 leaderLsn 的第一条记录。
     *
     * <p>用于 Pull 复制判断能否从请求的 leaderLsn 连续读取。
     * 如果返回的 key 大于请求 leaderLsn，说明中间日志已经断代，需要 Full Sync。
     */
    public Map.Entry<Long, WalPosition> ceiling(long leaderLsn){
        return index.ceilingEntry(leaderLsn);
    }

    /**
     * 当前索引中最早的 leaderLsn。
     */
    public Long firstLeaderLsn(){
        return index.isEmpty() ? null : index.firstKey();
    }

    /**
     * 当前索引中最新的 leaderLsn。
     */
    public Long lastLeaderLsn(){
        return index.isEmpty() ? null : index.lastKey();
    }

    /**
     * 清空索引。
     *
     * <p>启动恢复或全量重建索引前使用。
     */
    public void clear(){
        index.clear();
    }

    /**
     * 当前索引是否为空。
     */
    public boolean isEmpty(){
        return index.isEmpty();
    }
}
