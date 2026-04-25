package com.bitark.engine.replication.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "replication")
@Data
public class ReplicationConfig {

    /**
     * 旧 Push 模式遗留字段。
     *
     * <p>在 Push 模式里，Master 会主动向该地址推送单条复制请求。
     * 当前主链路已经切到 Pull，这个字段不再参与 Pull 主流程，
     * 仅作为历史代码保留，后续可以整体删除。</p>
     */
    private String slaveUrl = "http://127.0.0.1:8081/internal/sync";


    /**
     * ISR 判定时允许的最大落后字节数。
     * 这是复制治理参数，不是 Pull 批量抓取大小参数。
     */
    private long maxLagBytes = 100;

    /**
     * ISR 进入需要的连续健康次数。
     */
    private int isrJoinStreak = 3;

    /**
     * Master 节点地址。
     * SlavePullScheduler 会向该地址发起 /internal/fetch 请求。
     */
    private String masterUrl;

    /**
     * 当前节点自己的地址。
     * 用于告诉 Master“我是谁”，例如 register / heartbeat / fetch 请求中会带上该值。
     */
    private String selfUrl;

    /**
     * 本地复制进度文件路径。
     */
    private String progressPath = "/home/qiushui/IdeaProjects/BitArk/wal/slave-progress.bin";

    /**
     * 心跳间隔（Slave 发）。
     * 这是存活检测参数，不等于 Pull 调度频率。
     */
    private long heartbeatIntervalMs = 2000;

    /**
     * Master 端判定 Slave 失活的阈值。
     */
    private long heartbeatTimeoutMs = 10000;

    /**
     * Pull 模式下的拉取周期。
     */
    private long fetchIntervalMs = 500;

    /**
     * Pull 模式下一次 fetch 的最大批量字节数。
     * 它控制单次网络请求和单批应用的数据量上限。
     */
    private int fetchBatchBytes = 64 * 1024;

    private boolean pullEnabled = false;

}
