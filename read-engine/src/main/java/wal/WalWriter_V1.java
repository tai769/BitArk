package wal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * WalWriter
 * 职责：负责将 LogEntry 高效、持久化地写入磁盘。
 * 特性：异步 IO、Group Commit、FileChannel 顺序写。
 */
public class WalWriter_V1 implements AutoCloseable{
    private static final Logger logger = LoggerFactory.getLogger(WalWriter_V1.class);

    // 配置参数
    private static final int QUEUE_SIZE = 10000;
    private static final int BUFFER_SIZE = 1024 * 64; // 64KB Write Buffer
    private static final long COMMIT_INTERVAL_MS = 10; // Group Commit 最大等待时间
    // 文件与通道
    private final FileChannel fileChannel;
    private final ByteBuffer writeBuffer;

    // 异步队列 (Partition Thread -> WalWriter Thread)
    private final BlockingQueue<LogEntry> queue;

    // 刷盘线程
    private final Thread ioThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public WalWriter_V1(String filePath) throws IOException{
        File file = new File(filePath);
        if (!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        // 使用 FileOutputStream 获取 Channel，append 模式
        // 实际生产中可能需要 RandomAccessFile 来做更复杂的预分配
        this.fileChannel = new FileOutputStream(file, true).getChannel();
        this.writeBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        // 有界队列，提供背压 (Backpressure) 机制，防止内存溢出
        this.queue = new ArrayBlockingQueue<>(QUEUE_SIZE);

        // 启动后台 IO 线程
        this.ioThread = new Thread(this::ioLoop, "wal-writer-thread");
        this.ioThread.start();
    }
    public  void append(LogEntry entry) throws  InterruptedException{
        if (!running.get()){
            throw new IllegalStateException("WalWriter is closed");
        }
        //如果队列满了，会阻塞业务线程（背压），防止数据丢失
        queue.put(entry);
    }

    /*
     * 核心IO循环
     */
    private void ioLoop(){
        List<LogEntry> batchBuffer = new ArrayList<>(QUEUE_SIZE);
        while (running.get() || !queue.isEmpty()){
            try {
                //1. 尝试获取锁
                // 策略：如果 buffer 里有数据，只 poll 很短时间；如果 buffer 空，可以阻塞等久一点
                LogEntry first = queue.poll(COMMIT_INTERVAL_MS, TimeUnit.MICROSECONDS);
                if (first == null){
                    // 超时未获取到数据，如果 buffer 里有未落盘的数据，强制刷盘
                    if (writeBuffer.position() > 0){
                        flushToDisk();
                    }
                    continue;
                }
                //2.  批量处理 (Drain queue)
                batchBuffer.add(first);
                queue.drainTo(batchBuffer, QUEUE_SIZE -1);

                //3. 序列化到 DirectBuffer
                for (LogEntry entry : batchBuffer){
                    // 检查 Buffer 是否有足够空间 (LogEntry定长21字节)
                    if (writeBuffer.remaining() < LogEntry.ENTRY_SIZE){
                        flushToDisk();  // 空间不足，先刷盘
                    }
                    entry.encode(writeBuffer);
                }
                    // 4. 数据已入 Buffer，根据策略决定是否立即落盘
                    // 这里采用激进策略：只要有数据写入了 Buffer，且本次 batch 处理完了，就尝试刷盘
                    // 或者判断 writeBuffer 是否达到一定水位
                    if (writeBuffer.position() > 0){
                        flushToDisk();
                    }
                    batchBuffer.clear();
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
                break;
            }catch (Exception e){
                logger.error("WalWriter IO Error", e);
                // 生产环境这里需要严谨的错误处理，甚至停机，因为 WAL 坏了意味着数据不一致
            }
        }
    }
    /**
     * 执行真正的IO操作
     */
    private void flushToDisk() throws IOException{
        if (writeBuffer.position() == 0) return;
        //1. 切换读写模式
        writeBuffer.flip();

        //2. 写入OS Page Cache
        while (writeBuffer.hasRemaining()){
            fileChannel.write(writeBuffer);
        }

        //3. 强制刷盘 fsync
        fileChannel.force(false);

        //4. 重置buffer
        writeBuffer.clear();
    }

    @Override
    public void close() throws Exception {
        running.set(false);
        try{
            ioThread.join(1000);
            if (writeBuffer.position() > 0){
                flushToDisk();
            }
            fileChannel.close();
        }catch (Exception e){
            logger.error("WalWriter Close Error", e);
        }
    }
}
