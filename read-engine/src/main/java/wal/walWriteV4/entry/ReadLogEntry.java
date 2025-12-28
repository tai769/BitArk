package wal.walWriteV4.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadLogEntry implements LogEntry{
    private byte type;
    private Long userId;
    private Long msgId;

    public static final int ENTRY_SIZE = 21;  //1 + 8 + 8 + 4(数据校验）
    @Override
    public int size() {
        return ENTRY_SIZE;
    }

    /***
     * encode:
     * + 不创建新 byte[]
     * + 直接写入外部 buffer（零拷贝）
     * + 最终返回整个 entry 的字节数组（方便某些 WAL 使用场景）
     */
    @Override
    public byte[] encode(ByteBuffer buffer) {

        int startPos = buffer.position();

        buffer.put(type);
        buffer.putLong(userId);
        buffer.putLong(msgId);

        // 计算 CRC (前17字节)
        CRC32 crc32 = new CRC32();
        ByteBuffer slice = buffer.duplicate();
        slice.position(startPos);
        slice.limit(startPos + 17);
        crc32.update(slice);

        buffer.putInt((int) crc32.getValue());

        // 返回一份 entry 的 byte[]，但不打乱 buffer 的 position
        byte[] arr = new byte[ENTRY_SIZE];
        int oldPos = buffer.position();
        buffer.position(startPos);
        buffer.get(arr);
        buffer.position(oldPos);

        return arr;
    }

    @Override
    public void decode(ByteBuffer buffer) {
        int startPos = buffer.position();

        byte t = buffer.get();
        long uid = buffer.getLong();
        long mid = buffer.getLong();
        int storedCrc = buffer.getInt();

        // 计算 CRC
        CRC32 crc32 = new CRC32();
        ByteBuffer slice = buffer.duplicate();
        slice.position(startPos);
        slice.limit(startPos + 17);
        crc32.update(slice);

        if ((int) crc32.getValue() != storedCrc) {
            throw new RuntimeException("CRC 校验失败，数据损坏!");
        }

        this.type = t;
        this.userId = uid;
        this.msgId = mid;
    }
    @Override
    public String toString() {
        return "ReadLogEntry{" +
                "type=" + type +
                ", userId=" + userId +
                ", msgId=" + msgId +
                '}';
    }
}
