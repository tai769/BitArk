package com.bitark.engine.recover;

import com.bitark.engine.service.apply.ReadStateMachineApplier;
import com.bitark.engine.wal.LogEngine;
import com.bitark.engine.wal.WalEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecoveryCoordinatorConfig {

    @Bean
    public RecoveryCoordinator recoveryCoordinator(WalEngine walEngine,
                                                   ReadStateMachineApplier applier,
                                                   RecoveryConfig recoveryConfig,
                                                   LogEngine logEngine) {
        return new RecoveryCoordinatorImpl(walEngine, applier, recoveryConfig, logEngine);
    }
}
