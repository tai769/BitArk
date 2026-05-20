package com.bitark.commons.log;

/**
 * WAL replay 回调接口。
 *
 * <p>WalReader 在恢复 WAL 时，每成功解析并校验出一条 WalRecord，
 * 就通过该接口交给上层状态机处理。
 *
 * <p>这个接口只传递通用 WalRecord，不解释 payload 的业务含义。
 * payload 的解释应该交给业务层 dispatcher / command codec。
 */
@FunctionalInterface
public interface WalRecordHandler {

    /**
     * 处理一条已经通过 magic、version、crc 校验的 WAL record。
     */
    void handle(WalRecord record);
}
