package test;

import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.wal.WalEngine;
import com.bitark.engine.wal.WalEngines;
import com.bitark.commons.log.LogEntry;
import com.bitark.engine.config.WalConfig;

public class WalCrashSafeTest {

    public static void main(String[] args) {
         // 第一次运行：写入并“崩溃前查看内存状态”
        try {
            writePhase();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 第二次运行：只通过 WAL 回放恢复内存，再检查状态
        try {
            recoverPhase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writePhase() throws Exception {
        WalConfig config = new WalConfig();
        WalEngine wal = WalEngines.createEngine(config);
        ReadStatusEngine engine = new ReadStatusEngine();  // 可以指定 Set / Roaring 模式

        long userId1 = 1L, userId2 = 2L;
        long[] msgs1 = {1L, 2L, 3L};
        long[] msgs2 = {10L, 20L, 30L};

        // 1. 写入一些数据
        for (long msgId : msgs1) {
            LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId1, msgId);
            wal.append(entry);
            engine.markRead(userId1, msgId);
        }
        for (long msgId : msgs2) {
            LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId2, msgId);
            wal.append(entry);
            engine.markRead(userId2, msgId);
        }

        // 2. 打印崩溃前的内存状态
        System.out.println("=== 崩溃前内存状态 ===");
        System.out.println("u1-1: " + engine.isRead(userId1, 1L));
        System.out.println("u1-2: " + engine.isRead(userId1, 2L));
        System.out.println("u2-10: " + engine.isRead(userId2, 10L));

        // 3. 模拟进程宕机：这里就不调用 wal.close()，直接退出方法
        // （真实 kill -9 也是不走 close 的）
    }

     private static void recoverPhase() throws Exception {
        WalConfig config = new WalConfig();
        WalEngine wal = WalEngines.createEngine(config);
        ReadStatusEngine engine = new ReadStatusEngine();

        // 1. 利用 WAL 回放恢复内存
        wal.replay(entry -> {
            if (entry.getType() == LogEntry.READ_ENTRY) {
                engine.markRead(entry.getUserId(), entry.getMsgId());
            }
        });

        // 2. 检查恢复后的状态是否和崩溃前一致
        long userId1 = 1L, userId2 = 2L;
        System.out.println("=== 恢复后内存状态 ===");
        System.out.println("u1-1: " + engine.isRead(userId1, 1L));
        System.out.println("u1-2: " + engine.isRead(userId1, 2L));
        System.out.println("u2-10: " + engine.isRead(userId2, 10L));
    }

}
