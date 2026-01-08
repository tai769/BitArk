package com.bitark.wal.config;

import com.bitark.enums.WalMode;

import lombok.Data;

@Data
public class WalConfig {

    // 日志目录
    private final String walDir = "/dtat/wal";

    private final String walFileName = "wal.log";

    private final WalMode walMode = WalMode.GROUP_COMMIT;

    public String getWalPath() {
        return walDir + "/" + walFileName; 
    }

}
