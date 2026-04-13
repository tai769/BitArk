package com.bitark.commons.dto;

import lombok.Data;

import java.util.List;

@Data
public class FetchResponse {

    /**
     * 本次拉取结果状态。
     */
    private FetchStatus status;

    /**
     * Pull 模式下的批次游标。
     * Slave 成功应用完本批数据后，下一次应从该位置继续 fetch。
     */
    private Long nextLsn;

    /**
     * Pull 模式下返回的一批业务记录。
     * 这里不再复用 Push 模式的 ReplicationRequest，避免把单条复制语义和批量拉取语义混在一起。
     */
    private List<FetchEntryDTO> entries;
}
