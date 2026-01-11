package com.bitark.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.WalEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;
import com.bitark.thread.ThreadUtils;
import com.bitark.log.LogEntry;




@Slf4j
public class ReadServiceImpl implements ReadService {

  ReadStatusEngine engine = new ReadStatusEngine();

  private final RestTemplate restTemplate = new RestTemplate();

  private ExecutorService executorService = ThreadUtils.newThreadPoolExecutor(32, 64, 1000L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<>(),
      "sync",
      true);

  private final WalEngine walEngine;

  public ReadServiceImpl(WalEngine walEngine) throws Exception {
    this.walEngine = walEngine;
    
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
    // 1. 阅读以前的数据 然后恢复内存数据
    walEngine.replay(entry -> {
      engine.markRead(entry.getUserId(), entry.getMsgId());
    });
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

}
