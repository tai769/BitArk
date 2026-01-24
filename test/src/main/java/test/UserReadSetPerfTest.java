package test;

import com.bitark.engine.ReadStatusEngine;
import com.bitark.enums.UserReadSetMode;

public class UserReadSetPerfTest {
    public static void main(String[] args) {
        testWithMode(UserReadSetMode.SET);
        testWithMode(UserReadSetMode.ROARING);

    }

    private static void testWithMode(UserReadSetMode mode) {
        System.out.println("==== 测试模式: " + mode + " ====");


        // 创建ReadStatusEngine实例，传入指定的模式
        ReadStatusEngine engine = new ReadStatusEngine(mode);
        // 提示已创建引擎实例
        System.out.println("已创建引擎实例，模式: " + mode);

        int userCount = 10000;
        int perUserMsg = 10000;
            // 写入阶段
        long startWrite = System.currentTimeMillis();
        for (long userId = 1; userId <= userCount; userId++) {
            for (long msgId = 1; msgId <= perUserMsg; msgId++) {
                engine.markRead(userId, msgId);
            }
        }
        long endWrite = System.currentTimeMillis();

        // 读取阶段（简单顺序读一遍）
        long startRead = System.currentTimeMillis();
        long hit = 0;
        for (long userId = 1; userId <= userCount; userId++) {
            for (long msgId = 1; msgId <= perUserMsg; msgId++) {
                if (engine.isRead(userId, msgId)) {
                    hit++;
                }
            }
        }
        long endRead = System.currentTimeMillis();

        System.out.println("用户数: " + userCount + ", 每用户消息: " + perUserMsg);
        System.out.println("命中条数: " + hit);
        System.out.println("写入耗时: " + (endWrite - startWrite) + " ms");
        System.out.println("读取耗时: " + (endRead - startRead) + " ms");
        System.out.println();
        }
}
