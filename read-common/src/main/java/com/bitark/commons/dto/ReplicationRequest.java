package com.bitark.commons.dto;

import lombok.Data;

@Data
public class ReplicationRequest {

    private Long userId;
    private Long msgId;
    
    // 关键：带上 Master 的坐标
    private int segmentIndex;
    private long offset;

}
