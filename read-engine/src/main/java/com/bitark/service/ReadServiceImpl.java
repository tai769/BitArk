package com.bitark.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.WalEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;
import com.bitark.thread.ThreadUtils;
import com.bitark.util.SnapshotManager;
import com.bitark.log.LogEntry;




@Slf4j
public class ReadServiceImpl implements ReadService {

  private static final String SNAPSHOT_PATH = "/home/qiushui/IdeaProjects/BitArk/snapshot.bin";

  ReadStatusEngine engine = new ReadStatusEngine();

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
  public void recover() throws Exception {
    log.info("开始恢复内存状态...");
    Path snapshotPath = Paths.get(SNAPSHOT_PATH);
    
    try {
      // 1. 尝试从 snapshot 恢复
      if (Files.exists(snapshotPath)) {
        log.info("发现 snapshot 文件: {}", snapshotPath);
        snapshotManager.load(engine);
        log.info("✅ Snapshot 恢复成功");
      } else {
        log.warn("⚠️  Snapshot 文件不存在，将全量从 WAL 恢复");
      }
    } catch (Exception e) {
      log.error("❌ Snapshot 读取失败: {}，将全量从 WAL 恢复", e.getMessage());
    }
   
    // 2. 从 WAL 回放（现在还是全量 replay，后续加 checkpoint 后会改成增量）
    log.info("开始从 WAL 回放...");
    walEngine.replay(entry -> {
      engine.markRead(entry.getUserId(), entry.getMsgId());
    });
    log.info("✅ WAL 回放完成");
    log.info("✅ 内存状态恢复完成");
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
  }





}
