package com.bitark.engine.wal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAL 物理位置。
 *
 * <p>WalPosition 描述某个 leaderLsn 在本机 WAL segment 文件中的具体位置。
 *
 * <p>它只属于 WAL 存储层，不能泄露到复制协议、Full Sync 协议和业务状态机层。
 * 上层统一使用 leaderLsn，只有 WAL 内部需要知道 segmentIndex 和 offset。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalPosition {

    /**
     * WAL segment 文件编号。
     *
     * <p>例如 wal.log.3 的 segmentIndex 就是 3。
     */
    private long segmentIndex;

    /**
     * 该 record 在 segment 文件内的起始 offset。
     */
    private long offset;

    /**
     * 该 record 的完整字节长度。
     *
     * <p>变长 WAL 必须保存 recordLength，否则 reader 只能 decode 当前 record，
     * 但无法稳定知道下一条 record 从哪里开始。
     */
    private int recordLength;
}
