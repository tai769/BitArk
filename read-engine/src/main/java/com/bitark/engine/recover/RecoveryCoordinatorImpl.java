package com.bitark.engine.recover;

import com.bitark.commons.wal.WalCheckpoint;
import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.checkpoint.CheckpointManager;
import com.bitark.engine.wal.WalEngine;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class RecoveryCoordinatorImpl implements RecoveryCoordinator {


    private final WalEngine walEngine;
    private final SnapshotManager snapshotManager;
    private final CheckpointManager checkpointManager;
    private final RecoveryConfig recoveryConfig;


    public RecoveryCoordinatorImpl(WalEngine walEngine, RecoveryConfig recoveryConfig) {
        this.walEngine = walEngine;
        this.recoveryConfig = recoveryConfig;
        this.snapshotManager = new SnapshotManager(Paths.get(recoveryConfig.getSnapshotPath()));
        this.checkpointManager = new CheckpointManager(Paths.get(recoveryConfig.getCheckpointPath()));
    }
    @Override
    public void recover(ReadStatusEngine engine) throws Exception {
        Path snapshotPath = Paths.get(recoveryConfig.getSnapshotPath());
        WalCheckpoint localCheckpoint = null;
        try{
            if (Files.exists(snapshotPath)){
                snapshotManager.load(engine);
                log.info("Snapshot loaded from {}", snapshotPath);

            }else {
                log.error("Snapshot not found at {}", snapshotPath);
            }
        }catch (Exception e){
            log.error("Failed to load snapshot from {}", snapshotPath, e);
        }
        try{
            localCheckpoint = checkpointManager.load();
        }catch (NoSuchFileException | FileNotFoundException e){
            log.error("Failed to load checkpoint from {}", recoveryConfig.getCheckpointPath(), e);
        }catch (Exception e){
            log.error("Failed to load checkpoint from {}", recoveryConfig.getCheckpointPath(), e);
        }

        if (localCheckpoint == null){
            walEngine.replay(entry -> engine.markRead(entry.getUserId(), entry.getMsgId()));
        }else {
            walEngine.replayFrom(localCheckpoint, entry -> engine.markRead(entry.getUserId(), entry.getMsgId()));
        }
        log.info("Recovery completed.");




    }

    @Override
    public WalCheckpoint snapshot(ReadStatusEngine engine) throws Exception {
        log.info("开始保存 snapshot...");
        snapshotManager.save(engine);
        log.info("✅ Snapshot 已保存到: {}", recoveryConfig.getSnapshotPath());

        WalCheckpoint cp = walEngine.currCheckpoint();
        checkpointManager.save(cp);
        return cp;
    }
}
