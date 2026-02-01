package com.bitark.engine.service.facade;



import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.recover.RecoveryCoordinator;
import com.bitark.engine.replication.bootstrap.ReplicationBootstrapper;
import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.sender.ReplicationSender;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import com.bitark.engine.service.command.ReadCommandService;
import com.bitark.engine.service.command.ReadCommandServiceImpl;
import com.bitark.engine.service.query.ReadQueryService;
import com.bitark.engine.service.query.ReadQueryServiceImpl;
import com.bitark.engine.wal.WalEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ServiceConfig {

    @Bean
    public ReadStatusEngine readStatusEngine() {
        return new ReadStatusEngine();
    }

    @Bean
    public ReadCommandService readCommandService(WalEngine walEngine,
                                                 ReadStatusEngine engine,
                                                 ReplicationSender sender,
                                                 ReplicationProgressStore store) {
        return new ReadCommandServiceImpl(walEngine, engine, sender, store);
    }

    @Bean
    public ReadQueryService readQueryService(ReadStatusEngine engine) {
        return new ReadQueryServiceImpl(engine);
    }

    @Bean
    public ReadServiceImpl readService(ReadCommandService cmd,
                                       ReadQueryService query,
                                       ReadStatusEngine engine,
                                       RecoveryCoordinator recoveryCoordinator,
                                       ReplicationBootstrapper bootstrapper,
                                       ReplicationTracker tracker,
                                       WalEngine walEngine) {
        return new ReadServiceImpl(cmd, query, engine, recoveryCoordinator, bootstrapper, tracker, walEngine);
    }
}