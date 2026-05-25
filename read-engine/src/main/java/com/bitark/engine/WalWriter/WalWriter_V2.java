package com.bitark.engine.WalWriter;

import com.bitark.commons.log.WalRecord;
import com.bitark.commons.log.WalRecordCodec;
import com.bitark.engine.wal.WalIndex;
import com.bitark.engine.wal.WalPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import com.bitark.commons.wal.WalCheckpoint;
import com.bitark.engine.WalReader.WalReader;
import com.bitark.engine.wal.WalConfig;

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


    private final WalIndex walIndex;
    private final String baseDir;
    private final String baseFileName;
    private final long maxFileSizeBytes; 
    private int currentIndex; // 当前文件索引
    private FileChannel fileChannel;
    private final BlockingQueue<WriteRequest> queue;
    private final ByteBuffer writeBuffer;
    private final AtomicBoolean running = new AtomicBoolean(true);
    // 单例模式
    private static WalWriter_V2 instance;



    private Thread ioThread;

    // 初始化
    public static WalWriter_V2 init(WalConfig config, WalIndex walIndex) throws IOException {
        
        String baseDir = config.getWalDir();
        String baseFileName = config.getWalFileName();
        Long maxFileSizeBytes = config.getMaxFileSizeBytes();

        //决定从那个index开始
        int startIndex = findMaxIndexFromDir(baseDir, baseFileName); // 简单实现: 无文件则返回

        // 计算当前要replay的文件路径(只针对最后一个文件回放,后续在完善)
        String path = buildFilePath(baseDir, baseFileName, startIndex);

        WalReader reader = new WalReader();
        long initPosition;
        try {
            initPosition = reader.replay(path, (WalRecord record) -> {
                //暂时只是打印,真正的恢复在WalEngine.replay做
                log.debug("Replay record: {}", record);
            });
           
        } catch (Exception e) {
            // TODO Auto-generated catch block
            log.error("初始化失败");
            initPosition = 0L;
        }
        return new WalWriter_V2(baseDir, baseFileName, maxFileSizeBytes, startIndex, initPosition,walIndex);

    }

    private static int findMaxIndexFromDir(String dir, String baseFileName) {
        File folder = new File(dir);
        if (!folder.exists()) {
            return 0;
        }
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return 0;
        }
        int maxIndex = 0;
        for (File file : files) {
            String name = file.getName();
            if (!name.startsWith(baseFileName + ".")) {
                continue;
            }
            //解析后缀的数字部分
            String suffixe = name.substring((baseFileName + ".").length());
            try{
                int idx = Integer.parseInt(suffixe);
                if (idx > maxIndex) {
                    maxIndex = idx;
                }
            } catch (NumberFormatException e) {
                //非数字后缀,忽略
            }
        }
        return maxIndex; 
        
    }

    private static String buildFilePath(String dir, String fileName, int index) {
        return dir + File.separator + fileName + "." + index;
    }

    public static WalWriter_V2 getInstance(WalConfig config,WalIndex walIndex) throws IOException {
        if (instance == null) {
            synchronized (WalWriter_V2.class) {
                if (instance == null) {
                    instance = init(config, walIndex);
                }
            }
        }
        return instance;
    }


    public WalWriter_V2(String baseDir, String baseFileName, Long maxFileSizeBytes, int startIndex, long initPosition,
                        WalIndex walIndex) throws IOException {
        this.baseDir = baseDir;
        this.baseFileName = baseFileName;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.currentIndex = startIndex;
        
        this.fileChannel = openChannelForIndex(currentIndex);
        this.fileChannel.position(initPosition);
        this.writeBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        this.queue = new ArrayBlockingQueue<>(QUEUE_SIZE);
        this.walIndex = walIndex;
        this.ioThread = new Thread(this::ioLoop, "wal-v2-writer");
        this.ioThread.start();

    }


    // 打开指定 index 的文件（不存在则创建），并返回 FileChannel
    private FileChannel openChannelForIndex(int index) throws IOException {
        String path = buildFilePath(baseDir, baseFileName, index);
       File file = new File(path);
       File parent = file.getParentFile();
       if (parent != null && !parent.exists()) {
           parent.mkdirs();
       }
       return new RandomAccessFile(file, "rw").getChannel();
    }




    /*
     * 优化点2 ： 返回Future, 支持强一致性等待
     */
    public CompletableFuture<Long> append(WalRecord record) {
        if (!running.get()) {
            throw new IllegalStateException("WalWriter is closed");
        }
        WriteRequest req = new WriteRequest(record);
        if (!queue.offer(req)) {
            // 队列满
            CompletableFuture<Long> fail = new CompletableFuture<>();
            fail.completeExceptionally(new RuntimeException("WalWriter queue is full"));
            return fail; // Changed from return fail.completedFuture(true);
        }
        return req.futrue; // Changed from return req.futrue.complete(true);
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
                    byte[] encoded = WalRecordCodec.encode(req.record);
                    int recordLength = encoded.length;
                    ensureWritableSpace(recordLength);
                    WalPosition position = new WalPosition(
                            currentIndex,
                            fileChannel.position() + writeBuffer.position(),
                            recordLength
                    );
                    writeBuffer.put(encoded);
                    req.position = position;
                    req.completedLeaderLsn = req.record.getLeaderLsn();
                }
                flush();

                // 优化点4 落盘成功后， 统一回调
                for (WriteRequest req : batch) {
                    walIndex.put(req.record.getLeaderLsn(), req.position);
                    req.futrue.complete(req.completedLeaderLsn);
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



    //写空间前检查
    private void  ensureWritableSpace(int recordLength)throws IOException{
        if(recordLength > maxFileSizeBytes){
            throw new IOException("record  is too large");
        }
        if (recordLength > BUFFER_SIZE) {
            throw new IOException("record is larger than write buffer");
        }
        if (writeBuffer.remaining() < recordLength){
            flush();
        }
        long segmentLocalPos = fileChannel.position() + writeBuffer.position();
        if (segmentLocalPos + recordLength > maxFileSizeBytes){
            flush();
            rollToNextSegment();
        }
    }


    //gundong
    private void rollToNextSegment() throws IOException{
        fileChannel.close();
        currentIndex++;
        fileChannel = openChannelForIndex(currentIndex);
        fileChannel.position(0L);
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
        // force(false) 表示尽量只强制刷文件内容，不强制刷元数据。
        // 但如果文件长度增长，操作系统仍可能需要更新元数据。
        fileChannel.force(false);
        writeBuffer.clear();

        maybeRoll();
    }



    public void maybeRoll() throws IOException {
        Long size  = fileChannel.position(); // 文件写到哪里了
        if (size >= maxFileSizeBytes ) {
            //1. 关闭当前文件
            fileChannel.close();
            currentIndex++;
            fileChannel = openChannelForIndex(currentIndex);
            fileChannel.position(0L);
            log.info("Rolled to new file: {}", baseDir+File.separator+baseFileName);
        }
    }



    @Override
    public void close() throws Exception {
        if (running.compareAndSet(true, false)) {
            if (ioThread != null) {
                ioThread.interrupt();
                ioThread.join();
            }
            if (fileChannel != null) {
                flush();
                fileChannel.close();
            }
        }
    }


    public WalCheckpoint currentCheckpoint()throws IOException {
        long pos = fileChannel.position();
        return new WalCheckpoint(currentIndex, pos);
    }


    @Data
    private static class WriteRequest {
        private  WalRecord record;
        private WalPosition position;
        private CompletableFuture<Long> futrue = new CompletableFuture<>();
        Long completedLeaderLsn;
        WriteRequest(WalRecord record) {
            this.record = record;
        }
    }



}
