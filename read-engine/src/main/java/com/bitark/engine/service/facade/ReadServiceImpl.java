package com.bitark.engine.service.facade;

import com.bitark.commons.lsn.LsnPosition;
import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import com.bitark.engine.replication.bootstrap.ReplicationBootstrapper;
import com.bitark.engine.replication.sender.ReplicationSender;
import com.bitark.engine.service.command.ReadCommandService;
import com.bitark.engine.service.query.ReadQueryService;
import com.bitark.engine.wal.WalEngine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.bitark.engine.recover.RecoveryCoordinator;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.log.LogEntry;
import com.bitark.commons.wal.WalCheckpoint;




@Slf4j
public class ReadServiceImpl implements ReadService {

    private final ReadCommandService commandService;
    private final ReadQueryService queryService;
    private final ReadStatusEngine engine;
    private final RecoveryCoordinator recoveryCoordinator;
    private final ReplicationBootstrapper bootstrapper;
    private final ReplicationTracker tracker;
    private final WalEngine walEngine;

    public ReadServiceImpl(ReadCommandService commandService,
                           ReadQueryService queryService,
                           ReadStatusEngine engine,
                           RecoveryCoordinator recoveryCoordinator,
                           ReplicationBootstrapper bootstrapper,
                           ReplicationTracker tracker,
                           WalEngine walEngine) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.engine = engine;
        this.recoveryCoordinator = recoveryCoordinator;
        this.bootstrapper = bootstrapper;
        this.tracker = tracker;
        this.walEngine = walEngine;
    }

    // 只负责 wal的操作
    @Override
    public void read(Long userId, Long msgId) throws Exception {
        commandService.read(userId, msgId);
    }

    @Override
    @PostConstruct
    public void recover() throws Exception {
        recoveryCoordinator.recover(engine);
        bootstrapper.reportIfPresent();
    }



    @Override
    public boolean isRead(Long userId, Long msgId) {

        return queryService.isRead(userId, msgId);
    }

    @Override
    public void readFromMaster (Long userId, Long msgId) throws Exception {
        commandService.readFromMaster(userId, msgId);
    }

    @Override
    public void snapshot() throws Exception {
        WalCheckpoint masterCheckpoint = recoveryCoordinator.snapshot(engine);
        LsnPosition minLsn = tracker.getMinAckLsn();
        WalCheckpoint safeCheckpoint = (minLsn == null)
                ? masterCheckpoint
                : new WalCheckpoint(1, minLsn.getSegmentIndex(), minLsn.getOffset());
        walEngine.gcOldSegment(safeCheckpoint);

    }






}
