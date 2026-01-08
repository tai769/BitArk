package com.bitark.wal.walWriteV4.entry;

import java.nio.ByteBuffer;

public interface LogEntry {
    int size();
    byte[] encode(ByteBuffer buffer);
    void decode(ByteBuffer buffer);
}
