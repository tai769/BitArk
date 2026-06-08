package com.bitark.commons.log;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 WAL 逻辑记录。
 *
 * <p>WalRecord 表示复制状态机里的一条有序命令日志。
 * 它只描述“这条日志是什么”，不描述“这条日志写在哪个文件、哪个 offset”。
 *
 * <p>这层抽象的目的，是让 WAL 层彻底脱离已读业务。
 * WAL 只保存 type + payload，不理解 userId/msgId 等业务字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalRecord {

    /**
     * Master 分配的全局逻辑日志序号。
     *
     * <p>这是复制、恢复、Full Sync、ISR、Truncate 统一使用的坐标。
     * Slave 复制时必须原样保存，不能重新生成自己的 LSN。
     */
    private long leaderLsn;

    /**
     * Leader 任期。
     *
     * <p>当前可以先固定为 1。
     * 后续做 Leader 切换、日志截断、防脑裂时，epoch 会和 leaderLsn 一起判断日志是否属于同一条历史线。
     */
    private int epoch;

    /**
     * 命令类型。
     *
     * <p>WAL 层只保存 type，不解释它的业务含义。
     * 例如 type=1 可以表示 READ_MARK，后续也可以扩展 DELETE、CONFIG_CHANGE 等命令。
     */
    private short type;

    /**
     * 业务命令内容。
     *
     * <p>payload 是业务层自己编码出来的字节数组。
     * 对于已读业务，payload 可以是 userId + msgId。
     */
    private byte[] payload;

    /**
     * 返回 payload 的长度。
     *
     * <p>Codec 计算 recordLength 时会用到它。
     */
    public int payloadLength() {
        if (payload == null) {
            throw new IllegalStateException("payload must not be null");
        }
        return payload.length;
    }

    public  static  WalRecord create(short type, byte[] payload){
        return  new WalRecord(0,1,type,payload);
    }

    public boolean hasLeaderLsn(){
        return leaderLsn > 0;
    }
}
