package com.bitark.wal.walWriteV4;



import com.bitark.wal.walWriteV4.entry.ReadLogEntry;
import com.bitark.wal.walWriteV4.entry.WriteLogEntry;
import com.bitark.wal.walWriteV4.segment.Segment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WalDemo {

    public static void main(String[] args) throws Exception {

        Path walDir = Path.of("./wal-demo");

        // 写入阶段
        WalWriter_V4 writer = new WalWriter_V4(walDir);

        System.out.println("=== 写入三条日志 ===");
        writer.append(new WriteLogEntry((byte) 1, 1001, 2001)).join();
        writer.append(new WriteLogEntry((byte) 1, 1002, 2002)).join();
        writer.append(new WriteLogEntry((byte) 1, 1003, 2003)).join();

        writer.close();

        System.out.println("=== 模拟重启 ===");

        // 读取阶段
        List<ReadLogEntry> all = recover(walDir);

        System.out.println("=== 恢复结果 ===");
        all.forEach(System.out::println);


        System.out.println("\n=== 多线程高并发写入测试 ===");
        highConcurrencyTest(walDir);
    }

    // 恢复 WAL
    public static List<ReadLogEntry> recover(Path dir) throws IOException {
        Path file = dir.resolve("wal.log");

        Segment seg = new Segment(file, 0, file.toFile().length());
        ByteBuffer buf = seg.mmap.duplicate();

        List<ReadLogEntry> list = new ArrayList<>();

        while (buf.remaining() >= WriteLogEntry.ENTRY_SIZE) {
            ReadLogEntry e = new ReadLogEntry();
            try {
                e.decode(buf);
                list.add(e);
            } catch (Throwable t) {
                break; // 读到未写完的部分
            }
        }

        try {
            seg.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return list;
    }


    public static void highConcurrencyTest(Path dir) throws Exception {
        WalWriter_V4 writer = new WalWriter_V4(dir);

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            int v = i;
            futures.add(writer.append(
                    new WriteLogEntry((byte) 1, v, v * 10)
            ));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        writer.close();

        List<ReadLogEntry> list = recover(dir);

        System.out.println("恢复条数 = " + list.size());
    }
}
