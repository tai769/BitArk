package com.bitark.commons.dto;

import lombok.Data;

/**
 * Slave 发起 Full Sync 时提交给 Master 的请求。
 *
 * <p>它表达的不是“继续拉增量日志”，而是：
 * 当前 Slave 已经无法靠 WAL 增量追上，需要 Master 提供一份完整状态快照。</p>
 */
@Data
public class FullSyncRequest {
    /**
     * Slave 的唯一标识，当前用 selfUrl 表示。
     * Master 用它更新复制账本、打印日志和定位是哪台从节点触发了全量同步。
     */
    private String slaveUrl;

    /**
     * Slave 当前已经落地的复制进度。
     * 这个字段不是用来继续增量读取，而是让 Master 判断和记录“这个 Slave 断代到了哪里”。
     */
    private Long currentLsn;
}
