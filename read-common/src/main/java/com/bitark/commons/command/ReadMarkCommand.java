package com.bitark.commons.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已读标记命令。
 *
 * <p>这是已读业务写入状态机的最小命令，表示：
 * 将 userId 对应的 msgId 标记为已读。
 *
 * <p>它属于业务命令层，不属于 WAL 存储层。
 * WAL 只保存该命令编码后的 payload 字节。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadMarkCommand {

    /**
     * 用户 ID。
     */
    private long userId;

    /**
     * 消息 ID。
     */
    private long msgId;
}
