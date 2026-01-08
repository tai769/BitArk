package com.bitark.log;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    private byte type; //判断是否读取
    private Long userId;
    private Long msgId;

    public static final int ENTRY_SIZE = 21;  //1 + 8 + 8 + 4(数据校验）
    public static final byte READ_ENTRY = 1;

    public byte[] encode() {
        // 1. 申请一个 21 字节的缓冲区
        ByteBuffer buffer = ByteBuffer.allocate(ENTRY_SIZE);

        // 2. 依次填入数据
        buffer.put(type);
        buffer.putLong(userId);
        buffer.putLong(msgId);

        // 3. 计算 CRC
        // 我们的数据占用了前 17 个字节 (1+8+8)
        byte[] data = buffer.array(); // 拿到数组引用
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, 17); // 只算前 17 个字节的指纹
        long crcValue = crc32.getValue();

        // 4. 把 CRC 填入最后 4 个字节
        buffer.putInt((int) crcValue);

        // 5. 返回整个数组 (21字节)
        return buffer.array();
    }
    
    // 新增方法：直接将数据编码写入给定的 ByteBuffer
    public void encode(ByteBuffer buffer) {
        int startPosition = buffer.position();
        
        // 依次填入数据
        buffer.put(type);
        buffer.putLong(userId);
        buffer.putLong(msgId);
        
        // 保存当前位置
        int dataEndPosition = buffer.position();
        
        // 计算 CRC (前17个字节)
        // 创建一个视图来计算CRC，不影响原buffer的状态
        ByteBuffer slice = buffer.duplicate();
        slice.position(startPosition);
        slice.limit(dataEndPosition);
        
        CRC32 crc32 = new CRC32();
        crc32.update(slice);
        long crcValue = crc32.getValue();
        
        // 把 CRC 填入最后 4 个字节
        buffer.putInt((int) crcValue);
    }
    
    public static LogEntry decode(ByteBuffer buffer) {
        if (buffer.remaining() < ENTRY_SIZE) {
            throw new RuntimeException("Buffer too small to decode LogEntry");
        }
        int startPos = buffer.position();
        byte type = buffer.get();
        long userId = buffer.getLong();
        long msgId = buffer.getLong();
        int storedCrc = buffer.getInt();
        CRC32 crc32 = new CRC32();

        //这里其实是零拷贝技术
        ByteBuffer slice = buffer.duplicate();
        slice.position(startPos);
        slice.limit(startPos + 17);
        crc32.update(slice);

        //比对
        if ((int) crc32.getValue() != storedCrc) {
            throw new RuntimeException("CRC校验失败");
        }
        return new LogEntry(type, userId, msgId);
    }
}