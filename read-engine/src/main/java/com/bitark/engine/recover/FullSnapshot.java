package com.bitark.engine.recover;

import lombok.Data;

/**
 * 引擎内部的 Full Sync 快照结果。
 *
 * <p>它不是网络 DTO，而是 Master 在本地生成的一份一致性结果：
 * snapshotBytes 表示完整状态，snapshotLsn 表示这份状态对应的 WAL 进度。</p>
 */
@Data
public class FullSnapshot {
    /**
     * 这份快照覆盖到的全局 WAL LSN。
     */
    private final long snapshotLsn;

    /**
     * 序列化后的完整内存状态。
     */
    private final byte[] snapshotBytes;
}
