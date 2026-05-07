package com.bitark.commons.enums;

/**
 * BitArk 状态机命令类型定义。
 *
 * <p>这些 type 会写入 WalRecord header，用于告诉状态机如何解释 payload。
 *
 * <p>注意：CommandTypes 不是 WAL 文件格式字段定义。
 * WAL 只保存 type 数值，具体 type 语义由业务状态机解释。
 */
public final class CommandTypes {

    /**
     * 标记某个用户已读某条消息。
     */
    public static final short READ_MARK = 1;

    private CommandTypes() {
    }
}
