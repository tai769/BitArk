package com.bitark.commons.dto;

import lombok.Data;

import java.util.List;

@Data
public class FetchResponse {
    private FetchStatus status;
    private Long nextLsn;
    private List<ReplicationRequest> entries;
}
