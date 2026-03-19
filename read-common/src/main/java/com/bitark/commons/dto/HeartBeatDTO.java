package com.bitark.commons.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeartBeatDTO {
    private String slaveUrl; //selfUrl的唯一标记

    private Long globalLsn; // 主lsn位置

    private long timestampMs; //心跳发出时间

    private String protocolVer; //预留字段
}
