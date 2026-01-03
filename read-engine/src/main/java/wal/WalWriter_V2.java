package wal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class WalWriter_V2 implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(WalWriter_V2.class);

    // 配置
    private static final int QUEUE_SIZE = 10000;

    private static final int BUFFER_SIZE = 1024 * 64;

    private static final long MAX_WAIT_MS = 5; // group commit 策略调整

    // 单例模式
    private static WalWriter_V2 instance;

    // 初始化
    public static WalWriter_V2 init(String filePath) throws IOException {
        WalReader_V1 reader = new WalReader_V1();
        long initPosition;
        try {
            initPosition = reader.replay(filePath, (LogEntry entry) -> {
                log.info("Replay entry: {}", entry);
            });
            return new WalWriter_V2(filePath, initPosition);

        } catch (Exception e) {
            // TODO Auto-generated catch block
            log.error("初始化失败");
            return new WalWriter_V2(filePath, 0);
        }

    }

    public static WalWriter_V2 getInstance() throws IOException {
        if (instance == null) {
            synchronized (WalWriter_V2.class) {
                if (instance == null) {
                    instance = init("wal3.log");
                }
            }
        }
        return instance;
    }

    private final BlockingQueue<WriteRequest> queue;
    private final FileChannel fileChannel;
    private final ByteBuffer writeBuffer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public WalWriter_V2(String filePath, long initPosition) throws IOException {
        File file = new File(filePath);
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        // 不要预分配空间，把这个位置交给wal去读取 ,这里其实需要判断是否是预分配内存
        // 获取channel
        this.fileChannel = new RandomAccessFile(file, "rw").getChannel();
        // 生产环境需读取 Checkpoint 恢复 position，这里简化为从头写或追加

        this.fileChannel.position(initPosition);
        this.writeBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.queue = new ArrayBlockingQueue<>(QUEUE_SIZE);

        this.ioThread = new Thread(this::ioLoop, "wal-v2-writer");
    }

    @Override
    public void close() throws Exception {

    }

    @Data
    private static class WriteRequest {
        final LogEntry entry;
        final CompletableFuture<Boolean> futrue;

        WriteRequest(LogEntry entry) {
            this.entry = entry;
            this.futrue = new CompletableFuture<>();

        }
    }

    /*
     * 优化点2 ： 返回Future, 支持强一致性等待
     */
    public CompletableFuture<Boolean> append(LogEntry entry) {
        if (!running.get()) {
            throw new IllegalStateException("WalWriter is closed");
        }
        WriteRequest req = new WriteRequest(entry);
        if (!queue.offer(req)) {
            // 队列满
            CompletableFuture<Boolean> fail = new CompletableFuture<>();
            fail.completeExceptionally(new RuntimeException("WalWriter queue is full"));
            return fail;
        }
        return req.futrue;
    }

    private void ioLoop() {
        List<WriteRequest> batch = new ArrayList<>(QUEUE_SIZE);

        while (running.get()) {
            try {
                // 优化点3 ： 智能group提交
                // 此时并不一直阻塞，而是等待一会，如果没有数据且Buffer里面有货，降低延迟
                WriteRequest first = queue.poll(MAX_WAIT_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    if (writeBuffer.position() > 0) {
                        flush();
                    }
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch);

                // 序列化
                for (WriteRequest req : batch) {
                    if (writeBuffer.remaining() < LogEntry.ENTRY_SIZE) {
                        flush();
                    }
                    req.entry.encode(writeBuffer);
                }
                flush();

                // 优化点4 落盘成功后， 统一回调
                for (WriteRequest req : batch) {
                    req.futrue.complete(true);
                }
                batch.clear();
            } catch (Exception e) {
                logger.error("WalWriter IO Error", e);
                // 发生IO错误，必须通知业务 县城 失败
                for (WriteRequest req : batch) {
                    req.futrue.completeExceptionally(e);
                }
                batch.clear();
            }
        }
    }

    private void flush() throws IOException {
        if (writeBuffer.position() == 0) {
            return;
        }
        writeBuffer.flip();
        while (writeBuffer.hasRemaining()) {
            fileChannel.write(writeBuffer);
        }

        // 优化点5： force(false)
        // 因为预分配了文件大小，文件元数据size没变化，之更新内容
        // 传 false可以减少一次更新 Inode的IO操作
        fileChannel.force(false);
        writeBuffer.clear();
    }

}
