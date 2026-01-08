package com.bitark.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.bitark.engine.ReadStatusEngine;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.client.RestTemplate;
import com.bitark.thread.ThreadUtils;
import com.bitark.log.LogEntry;
import com.bitark.log.LogEntryHandler;
import com.bitark.wal.WalReader.WalReader_V1;
import com.bitark.wal.WalWriter.WalWriter_V2;


@Slf4j
public class ReadServiceImpl implements ReadService {

  ReadStatusEngine engine = new ReadStatusEngine();

  private final RestTemplate restTemplate = new RestTemplate();

  private ExecutorService executorService = ThreadUtils.newThreadPoolExecutor(32, 64, 1000L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<>(),
      "sync",
      true);

  // 只负责 wal的操作
  @Override
  public void read(Long userId, Long msgId) throws Exception {
    WalWriter_V2 walWriter = WalWriter_V2.getInstance();
    try {
      LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId, msgId);
      walWriter.append(entry);
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
      walWriter.close();
      log.error("read error", e);
    }
  }

  @Override
  public void recover() throws Exception {
    // 1. 阅读以前的数据 然后恢复内存数据
    WalReader_V1 walReader_V1 = new WalReader_V1();
    walReader_V1.replay("wal.log", new LogEntryHandler() {

      @Override
      public void handle(LogEntry entry) {
        engine.markRead(entry.getUserId(), entry.getMsgId());
      }
    });
  }

  @Override
  public boolean isRead(Long userId, Long msgId) {

    return engine.isRead(userId, msgId);
  }

  @Override
  public void readFromMaster (Long userId, Long msgId) throws Exception {
      log.info("readFromMaster");
      WalWriter_V2 walWriter = WalWriter_V2.getInstance();

      LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId, msgId);
      walWriter.append(entry);
      engine.markRead(userId, msgId);
  }

}
