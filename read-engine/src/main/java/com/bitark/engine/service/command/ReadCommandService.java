package com.bitark.engine.service.command;

import com.bitark.commons.dto.FetchResponse;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.log.WalRecord;

/**
 * 写路径命令服务接口。
 *
 * <p>这一层只负责编排“写命令如何落到本地状态”：
 * 1. 写入本地 WAL
 * 2. 更新本地内存状态
 * 3. 在复制场景下推进复制进度</p>
 */
public interface ReadCommandService {

    /**
     * Master 本地业务写入口。
     */
    void read(Long userId, Long msgId) throws Exception;

    /**
     * Slave 应用上游复制数据时的本地落地入口。
     */
    void applyReplicatedRead(WalRecord record) throws Exception;

    /**
     * Pull 模式下的批量复制应用入口。
     */
    void applyFetchBatch(FetchResponse fetchResponse) throws Exception;
}
