package com.bitark.commons.dto;
import lombok.Data;

@Data
public class ReplicationAck {

    private String slaveUrl; // slave的唯一标识
    private Long globalLsn;



}
