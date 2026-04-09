package com.bitark.commons.dto;

import lombok.Data;

@Data
public class FetchRequest {
    private String slaveUrl;
    private Long fromLsn;
    private Integer maxBytes;
}
