package com.bitark.engine;

import com.bitark.wal.config.WalConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class WalEngineConfig {

    @Bean
    public WalEngine walEngine(WalConfig walConfig) throws Exception {
        // 使用 Spring 注入的 WalConfig 创建 WalEngine
        return WalEngines.createEngine(walConfig);
    }
}
