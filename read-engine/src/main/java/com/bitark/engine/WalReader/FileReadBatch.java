package com.bitark.engine.WalReader;

import com.bitark.commons.log.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FileReadBatch {
    private List<LogEntry> entries;
    private long nextOffset;
}
