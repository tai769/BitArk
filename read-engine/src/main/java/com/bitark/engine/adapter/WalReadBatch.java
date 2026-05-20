package com.bitark.engine.adapter;

import com.bitark.commons.log.WalRecord;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * WAL 引擎跨 segment 聚合后的批量读取结果。
 *
 * <p>它和 FileReadBatch 的区别是：
 * FileReadBatch 只代表单个 segment 文件的一次读取；
 * WalReadBatch 代表 WalEngine 对外返回的一批连续 WalRecord，后续可能跨多个 segment。
 */
@Data
@AllArgsConstructor
public class WalReadBatch {

    /**
     * 本次读取到的通用 WAL records。
     */
    private List<WalRecord> records;

    /**
     * 下一次 Pull 应该从哪个 leaderLsn 继续。
     *
     * <p>注意：这是逻辑复制坐标，不是物理文件 offset。
     * 当前阶段 readBatch 还在迁移中，后面会结合 WalIndex 精确推进。
     */
    private Long nextLsn;
}
