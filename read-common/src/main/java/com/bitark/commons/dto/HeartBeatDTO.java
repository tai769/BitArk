package com.bitark.commons.dto;

import com.bitark.commons.lsn.LsnPosition;
import lombok.Data;

@Data
public class HeartBeatDTO {
    private String slaveId; //selfUrl的唯一标记

    private LsnPosition lsnPosition; // 主lsn位置

    private long timestampMs; //心跳发出时间

    private String protocolVer; //预留字段
}
