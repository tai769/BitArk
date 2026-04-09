package com.bitark.engine.adapter;

import com.bitark.commons.log.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class WalReadBatch {
    private List<LogEntry> entries;
    private Long nextLsn;
}
