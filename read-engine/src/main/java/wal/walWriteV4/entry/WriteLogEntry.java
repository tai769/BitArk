package wal.walWriteV4.entry;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class WriteLogEntry implements LogEntry {

    private final byte type;
    private final long userId;
    private final long msgId;

    public static final int ENTRY_SIZE = 1 + 8 + 8 + 4;  // 21字节

    public WriteLogEntry(byte type, long userId, long msgId) {
        this.type = type;
        this.userId = userId;
        this.msgId = msgId;
    }

    @Override
    public int size() {
        return ENTRY_SIZE;
    }

    @Override
    public byte[] encode(ByteBuffer buffer) {
        int startPos = buffer.position();

        buffer.put(type);
        buffer.putLong(userId);
        buffer.putLong(msgId);

        // 计算 CRC
        ByteBuffer slice = buffer.duplicate();
        slice.position(startPos);
        slice.limit(startPos + 17);

        CRC32 crc32 = new CRC32();
        crc32.update(slice);

        buffer.putInt((int) crc32.getValue());

        return null; // WALWriter 用 ByteBuffer，不需要返回数组
    }

    @Override
    public void decode(ByteBuffer buffer) {
        throw new UnsupportedOperationException("WriteLogEntry cannot decode.");
    }
}
