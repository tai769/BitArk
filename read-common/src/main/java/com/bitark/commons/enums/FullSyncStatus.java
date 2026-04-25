package com.bitark.commons.enums;

/**
 * Full Sync 的执行结果。
 *
 * <p>这里只表达协议层结果，不表达具体失败异常。
 * 具体原因放在 FullSyncResponse.message 中。</p>
 */
public enum FullSyncStatus {
    OK,
    FAILED
}
