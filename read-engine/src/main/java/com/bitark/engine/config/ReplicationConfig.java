package com.bitark.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "replication")
@Data
public class ReplicationConfig {

    // 默认值设为本地，防止 yml 漏写报错
    private String slaveUrl = "http://127.0.0.1:8081/internal/sync";

    private String masterUrl; //老师的地址

    private String selfUrl; //自己的地址

    private String progressPath = "/home/qiushui/IdeaProjects/BitArk/wal/slave-progress.bin";
}
