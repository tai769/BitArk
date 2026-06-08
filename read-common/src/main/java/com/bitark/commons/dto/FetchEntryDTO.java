package com.bitark.commons.dto;

import lombok.Data;

/**
 * Pull 模式下的一条批量返回记录。
 *
 * <p>它只承载业务数据本身，不承载 globalLsn。
 * Pull 模式的复制进度由 FetchResponse.nextLsn 统一推进，
 * 不再像 Push 模式那样由每条请求单独携带 globalLsn。</p>
 */
@Data
public class FetchEntryDTO {


    private Long leaderLsn;
    private Integer epoch;
    private Short type;
    private byte[] payload;
}
