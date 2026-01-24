package com.bitark.recover;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "recovery")
@Data
public class RecoveryConfig {

    /**
     * Snapshot 文件的绝对路径
     * 用于保存内存中的全量状态快照
     */
    private String snapshotPath = "/tmp/bitark/snapshot.bin";

    /**
     * Checkpoint 文件的绝对路径
     * 记录 Snapshot 对应的 WAL 位置(segmentIndex + offset)
     */
    private String checkpointPath = "/tmp/bitark/checkpoint.bin";
}
