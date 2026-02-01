package com.bitark.engine.recover;

import com.bitark.engine.wal.WalEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecoveryCoordinatorConfig {

    @Bean
    public RecoveryCoordinator recoveryCoordinator(WalEngine walEngine, RecoveryConfig recoveryConfig ) {
        return new RecoveryCoordinatorImpl(walEngine, recoveryConfig);
    }
}
