package com.bitark.commons.log;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * WalRecord 的二进制编解码规则。
 *
 * <p>WalRecord 负责表达“日志的逻辑含义”，WalRecordCodec 负责表达“日志在磁盘上怎么摆放”。
 *
 * <pre>
 * recordLength | magic | version | leaderLsn | epoch | type | payloadLength | payload | crc32
 * </pre>
 *
 * <p>这层抽象的目的，是把二进制格式从业务对象里拆出来。
 * 后续 WAL 格式升级时，优先修改 Codec，而不是污染 WalRecord 和业务状态机。
 */
public final class WalRecordCodec {

    /**
     * BitArk WAL record 魔数。
     *
     * <p>十六进制 42 41 52 4B，对应 ASCII 字符串 "BARK"。
     * Reader 用它快速判断当前位置是不是一条合法的 BitArk WAL 记录。
     */
    public static final int MAGIC = 0x4241524B;


    /**
     * WAL record 格式版本。
     *
     * <p>当前是 V1。后续如果格式增加字段，可以保留 V1 decoder，再新增 V2 decoder。
     */
    public static final short VERSION = 1;

    /**
     * 以下常量表示每个固定字段在二进制格式中占用的字节数。
     */
    public static final int RECORD_LENGTH_SIZE = 4;
    public static final int MAGIC_SIZE = 4;
    public static final int VERSION_SIZE = 2;
    public static final int LEADER_LSN_SIZE = 8;
    public static final int EPOCH_SIZE = 4;
    public static final int TYPE_SIZE = 2;
    public static final int PAYLOAD_LENGTH_SIZE = 4;
    public static final int CRC_SIZE = 4;

    /**
     * 固定 header 长度。
     *
     * <p>注意：这里包含 recordLength 字段自身，但不包含 payload 和 crc32。
     */
    public static final int FIXED_HEADER_SIZE =
            RECORD_LENGTH_SIZE
                    + MAGIC_SIZE
                    + VERSION_SIZE
                    + LEADER_LSN_SIZE
                    + EPOCH_SIZE
                    + TYPE_SIZE
                    + PAYLOAD_LENGTH_SIZE;

    private WalRecordCodec() {
    }

    /**
     * 计算一条 WalRecord 编码后的完整字节长度。
     *
     * <p>recordLength 的约定是：从 recordLength 字段开始，到 crc32 字段结束的总长度。
     * 因此下一条记录的位置可以直接用 currentOffset + recordLength 得到。
     */
    public static int recordLength(WalRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        return FIXED_HEADER_SIZE + record.payloadLength() + CRC_SIZE;
    }

    public static byte[] encode(WalRecord record){
        if (record == null){
            throw new IllegalArgumentException("record is null");
        }
        byte[] payload = record.getPayload();
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        int payloadLength = payload.length;
        int recordLength =  recordLength( record);

        ByteBuffer buffer = ByteBuffer.allocate(recordLength);

        int crcStart = buffer.position();
        buffer.putInt(recordLength);
        buffer.putInt(MAGIC);
        buffer.putShort(VERSION);
        buffer.putLong(record.getLeaderLsn());
        buffer.putInt(record.getEpoch());
        buffer.putShort(record.getType());
        buffer.putInt(payloadLength);
        buffer.put(payload);
        int crcEnd = buffer.position();

        CRC32 crc32 = new CRC32();
        ByteBuffer crcSlice = buffer.duplicate();
        crcSlice.position(crcStart);
        crcSlice.limit(crcEnd);
        crc32.update(crcSlice);
        buffer.putInt((int) crc32.getValue());
        return buffer.array();

    }


    public static WalRecord decode(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer is null");
        }
        if (buffer.remaining() < FIXED_HEADER_SIZE + CRC_SIZE){
            throw new IllegalArgumentException("buffer is too small");
        }
        int startPosition = buffer.position();
        int recordLength = buffer.getInt();
        if (recordLength < FIXED_HEADER_SIZE + CRC_SIZE) {
            throw new IllegalArgumentException("recordLength is too small");
        }
        if(buffer.limit() - startPosition < recordLength){
            throw new IllegalArgumentException("buffer is too small");
        }
        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("magic is not correct");
        }
        short version = buffer.getShort();
        if (version != VERSION) {
            throw new IllegalArgumentException("version is not correct");
        }
        long leaderLsn = buffer.getLong();
        int epoch = buffer.getInt();
        short type = buffer.getShort();
        int payloadLength = buffer.getInt();
        if (payloadLength < 0){
            throw new IllegalArgumentException("payloadLength is negative");
        }
        int expectedRecordLength = FIXED_HEADER_SIZE + payloadLength + CRC_SIZE;
        if (recordLength != expectedRecordLength){
            throw new IllegalArgumentException("recordLength is not correct");
        }
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        int crcEnd = buffer.position();
        int storedCrc = buffer.getInt();
        CRC32 crc32 = new CRC32();
        ByteBuffer crcSlice = buffer.duplicate();
        crcSlice.position(startPosition);
        crcSlice.limit(crcEnd);
        crc32.update(crcSlice);
        if (storedCrc != (int) crc32.getValue()){
            throw new IllegalArgumentException("crc is not correct");
        }
        buffer.position(startPosition + recordLength);
        return new WalRecord(leaderLsn, epoch, type, payload);
    }
}
