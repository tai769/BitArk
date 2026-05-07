package com.bitark.commons.command;

import java.nio.ByteBuffer;

/**
 * ReadMarkCommand 的 payload 编解码器。
 *
 * <p>它只负责把已读业务命令转换成 WalRecord.payload，
 * 不负责 WAL header、CRC、leaderLsn、epoch 等通用日志字段。
 *
 * <pre>
 * userId | msgId
 * 8 bytes + 8 bytes
 * </pre>
 */
public final class ReadMarkCommandCodec {

    /**
     * ReadMarkCommand payload 固定长度。
     *
     * <p>当前命令只包含两个 long：userId 和 msgId。
     */
    public static final int PAYLOAD_SIZE = 8 + 8;

    private ReadMarkCommandCodec() {
    }

    /**
     * 将已读命令编码成 WalRecord.payload。
     */
    public static byte[] encode(ReadMarkCommand command){
        if (command == null){
            throw new IllegalArgumentException("command must not be null");
        }
        ByteBuffer buffer = ByteBuffer.allocate(PAYLOAD_SIZE);
        buffer.putLong(command.getUserId());
        buffer.putLong(command.getMsgId());
        return buffer.array();
    }

    /**
     * 从 WalRecord.payload 还原已读命令。
     */
    public static ReadMarkCommand decode(byte[] payload){
        if(payload == null){
            throw new IllegalArgumentException("payload must not be null");
        }
        if (payload.length != PAYLOAD_SIZE){
            throw new IllegalArgumentException("payload size must be " + PAYLOAD_SIZE);
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        return new ReadMarkCommand(buffer.getLong(), buffer.getLong());
    }
}
