package com.bitark.commons.dto;

import lombok.Data;

/**
 * Push 模式下的单条复制命令 DTO。
 *
 * <p>在 Push 模式里，Master 每产生一条变更，就会立刻把这条变更发给 Slave。
 * 因此一条 ReplicationRequest 同时要表达两件事：
 * 1. 这条业务变更是什么（userId / msgId）
 * 2. 应用完这条变更后，Slave 的复制进度应该推进到哪里（globalLsn）</p>
 *
 * <p>所以 Push 模式下 globalLsn 是必须的。
 * 而在 Pull 模式里，复制进度由批次级别的 FetchResponse.nextLsn 统一表达，
 * 不再要求每条数据单独携带 globalLsn。</p>
 */
@Data
public class ReplicationRequest {

    private Long userId;
    private Long msgId;

    /**
     * Push 模式下的单条复制进度坐标。
     */
    private Long globalLsn;

}
