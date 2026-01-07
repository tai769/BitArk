package wal.config;

public class WalConfig {

    // 日志目录
    private final String walDir = "/dtat/wal";

    private final String walFileName = "wal.log";

    public String getWalPath() {
        return walDir + "/" + walFileName; 
    }

}
