package com.bitark.wal.config;

import enums.WalMode;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wal")
@Data
public class WalConfig {

    // 日志目录
    private  String walDir = "/home/qiushui/IdeaProjects/BitArk/wal";

    private  String walFileName = "wal.log";

    private  String mode = "group_commit";

    // 单个 WAL 文件的最大大小（单位：字节），比如默认 128MB
    private Long maxFileSizeBytes = 128L * 1024 * 1024;

    public String getWalPath() {
        return walDir + "/" + walFileName; 
    }

    public WalMode getWalMode() {
        return WalMode.getByCode(mode);
    }

}
