package com.bitark.engine.replication.master;

import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.slave.SlaveReplicationService;
import com.bitark.engine.replication.slave.SlaveReplicationServiceImpl;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import com.bitark.engine.service.command.ReadCommandService;
import com.bitark.engine.wal.WalEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReplicationServiceConfig {

    @Bean
    public MasterReplicationService masterReplicationService(ReplicationTracker tracker,
                                                             WalEngine engine) {
        return new MasterReplicationServiceImpl(tracker, engine);
    }

    @Bean
    public SlaveReplicationService slaveReplicationService(ReadCommandService readCommandService, ReplicationConfig replicationConfig) {
        return new SlaveReplicationServiceImpl(readCommandService, replicationConfig);
    }
}
