package com.bitark.engine.service;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.config.RecoveryConfig;
import com.bitark.engine.wal.WalEngine;
import com.bitark.engine.config.ReplicationConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;
import com.bitark.engine.recover.SnapshotManager;
import com.bitark.engine.checkpoint.CheckpointManager;
import com.bitark.commons.log.LogEntry;
import com.bitark.commons.wal.WalCheckpoint;




@Slf4j
public class ReadServiceImpl implements ReadService {


    private final RecoveryConfig recoveryConfig;


    ReadStatusEngine engine = new ReadStatusEngine();

    private CheckpointManager checkpointManager;

    private final RestTemplate restTemplate;

    private final ReplicationConfig replicationConfig;

    private final ExecutorService executorService;

    private final WalEngine walEngine;
    private SnapshotManager snapshotManager;

    public ReadServiceImpl(WalEngine walEngine, RecoveryConfig recoveryConfig, RestTemplate restTemplate, ReplicationConfig replicationConfig, ExecutorService executorService) throws Exception {
        this.recoveryConfig = recoveryConfig;
        this.restTemplate = restTemplate;
        this.replicationConfig = replicationConfig;
        this.executorService = executorService;
        this.walEngine = walEngine;
        this.snapshotManager = new SnapshotManager(Paths.get(recoveryConfig.getSnapshotPath()));
        this.checkpointManager = new CheckpointManager(Paths.get(recoveryConfig.getCheckpointPath()));
    }

    // 只负责 wal的操作
    @Override
    public void read(Long userId, Long msgId) throws Exception {
        try {
            LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId, msgId);
            walEngine.append(entry);
            engine.markRead(userId, msgId);

            /*
             * 发送同步请求
             */

            executorService.submit(() -> {
                // 1. 先获取路由
                String slaveUrl = replicationConfig.getSlaveUrl()+"?userId=" + userId + "&msgId=" + msgId;

                // 2. 发送同步请求
                try {
                    restTemplate.postForObject(slaveUrl, null, String.class);
                    log.info("sync success");
                } catch (Exception e) {
                    log.error("sync error", e);
                }

            });

        } catch (Exception e) {
            walEngine.close();
            log.error("read error", e);
        }
    }

    @Override
    @PostConstruct
    public void recover() throws Exception {
        log.info("开始恢复内存状态...");
        Path snapshotPath = Paths.get(recoveryConfig.getSnapshotPath());
        WalCheckpoint cp = null;

        // 1. 尝试 snapshot 恢复（失败也不中断）
        try {
            if (Files.exists(snapshotPath)) {
                snapshotManager.load(engine);
                log.info("✅ Snapshot 恢复成功");
            } else {
                log.warn("Snapshot 文件不存在，将依赖 WAL 恢复");
            }
        } catch (Exception e) {
            log.error("Snapshot 读取失败，将依赖 WAL 恢复: {}", e.getMessage());
        }

        // 2. 尝试读取 checkpoint（失败就退化成全量 replay）
        try {
            cp = checkpointManager.load();
        } catch (NoSuchFileException | FileNotFoundException e) {
            log.warn("Checkpoint 文件不存在，将使用全量 WAL 回放");
        } catch (Exception e) {
            log.error("Checkpoint 读取失败，将使用全量 WAL 回放", e);
        }

        // 3. 根据 cp 是否存在，决定用全量还是增量 replay
        if (cp == null) {
            walEngine.replay(entry -> engine.markRead(entry.getUserId(), entry.getMsgId()));
        } else {
            walEngine.replayFrom(cp, entry -> engine.markRead(entry.getUserId(), entry.getMsgId()));
        }

        log.info("✅ Recovery Complete. Engine instance ID: {}", System.identityHashCode(engine));
    }

    @Override
    public boolean isRead(Long userId, Long msgId) {

        return engine.isRead(userId, msgId);
    }

    @Override
    public void readFromMaster (Long userId, Long msgId) throws Exception {
        log.info("readFromMaster");
        LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId, msgId);
        walEngine.append(entry);
        engine.markRead(userId, msgId);
    }

    @Override
    public void snapshot() throws Exception {
        log.info("开始保存 snapshot...");
        snapshotManager.save(engine);
        log.info("✅ Snapshot 已保存到: {}", recoveryConfig.getSnapshotPath());

        WalCheckpoint cp = walEngine.currCheckpoint();
        log.info("Current checkpoint: {}", cp);
        checkpointManager.save(cp);

        //快照成功之后清理旧的segment
        walEngine.gcOldSegment(cp);
        log.info("✅ Old segments have been cleaned up");

    }





}
