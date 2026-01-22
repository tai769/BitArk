package com.bitark.service;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.bitark.engine.ReadStatusEngine;
import com.bitark.wal.WalEngine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;
import infrastructure.thread.ThreadUtils;
import com.bitark.recover.SnapshotManager;
import com.bitark.wal.checkpoint.CheckpointManager;
import com.bitark.wal.checkpoint.WalCheckpoint;
import log.LogEntry;




@Slf4j
public class ReadServiceImpl implements ReadService {

  private static final String SNAPSHOT_PATH = "/home/qiushui/IdeaProjects/BitArk/snapshot.bin";
  private static final String CHECKPOINT_PATH = "/home/qiushui/IdeaProjects/BitArk/wal/checkpoint.bin";

  ReadStatusEngine engine = new ReadStatusEngine();

  CheckpointManager checkpointManager = new CheckpointManager(Path.of(CHECKPOINT_PATH));

  private final RestTemplate restTemplate = new RestTemplate();

  private ExecutorService executorService = ThreadUtils.newThreadPoolExecutor(32, 64, 1000L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<>(),
      "sync",
      true);

  private final WalEngine walEngine;
  private final SnapshotManager snapshotManager;

  public ReadServiceImpl(WalEngine walEngine) throws Exception {
    this.walEngine = walEngine;
    this.snapshotManager = new SnapshotManager(Paths.get(SNAPSHOT_PATH));
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
        String slaveUrl = "http://127.0.0.1:8081/internal/sync?userId=" + userId + "&msgId=" + msgId;

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
    Path snapshotPath = Paths.get(SNAPSHOT_PATH);
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
    log.info("✅ Snapshot 已保存到: {}", SNAPSHOT_PATH);

    WalCheckpoint cp = walEngine.currCheckpoint();
    log.info("Current checkpoint: {}", cp);
    checkpointManager.save(cp);

    //快照成功之后清理旧的segment
    walEngine.gcOldSegment(cp);
    log.info("✅ Old segments have been cleaned up");
    
  }





}
