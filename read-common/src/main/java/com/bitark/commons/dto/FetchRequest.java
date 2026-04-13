package com.bitark.commons.dto;

import lombok.Data;

/**
 * Pull 模式下，Slave 向 Master 发起的一次拉取请求。
 *
 * <p>它描述的是：
 * 1. 我是谁（slaveUrl）
 * 2. 我已经追到哪里了（fromLsn）
 * 3. 我这次最多还能接收多少数据（maxBytes）</p>
 */
@Data
public class FetchRequest {
    private String slaveUrl;
    private Long fromLsn;
    private Integer maxBytes;
}
