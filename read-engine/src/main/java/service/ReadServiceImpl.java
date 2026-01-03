package service;

import engine.ReadStatusEngine;
import lombok.extern.slf4j.Slf4j;
import wal.LogEntry;
import wal.LogEntryHandler;
import wal.WalReader_V1;
import wal.WalWriter_V2;

@Slf4j
public class ReadServiceImpl implements ReadService {

  ReadStatusEngine engine = new ReadStatusEngine();

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

}
