package com.bitark.commons.dto;

import com.bitark.commons.enums.FullSyncStatus;
import lombok.Data;

/**
 * Master 返回给 Slave 的 Full Sync 响应。
 *
 * <p>它表达的是“一份完整状态 + 这份状态对应的 LSN”。Slave 应用成功后，
 * 必须把本地复制进度推进到 snapshotLsn，然后再从 snapshotLsn 继续 Pull 增量。</p>
 */
@Data
public class FullSyncResponse {

    /**
     * Full Sync 是否成功。
     */
    private FullSyncStatus status;

    /**
     * 这份快照对应的全局 LSN。
     * 语义：snapshotBytes 已经包含从系统开始到 snapshotLsn 为止的全部状态变更。
     */
    private Long snapshotLsn;

    /**
     * 序列化后的完整状态快照。
     * 当前阶段可以先用 byte[]，后续快照变大时再演进为分块传输。
     */
    private byte[] snapshotBytes;

    /**
     * 失败原因或调试信息。
     */
    private String message;
}
