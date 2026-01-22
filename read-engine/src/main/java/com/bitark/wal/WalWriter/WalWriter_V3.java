package com.bitark.wal.WalWriter;

import log.LogEntry;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WalWriter_V3 implements AutoCloseable{
    //map映射大小
    private static final int MAP_SIZE = 1024 * 1024 * 100;

    private final FileChannel fileChannel;

    private final MappedByteBuffer mappedBuffer;

    private final BlockingQueue<WriteRequest> queue;

    private final Thread ioThread;

    private final AtomicBoolean running = new AtomicBoolean(true);


    private static  class WriteRequest{
        final LogEntry entry;

        final CompletableFuture<Boolean> future;

        WriteRequest(LogEntry entry){
            this.entry = entry;
            this.future = new CompletableFuture<>();
        }
    }

    public WalWriter_V3(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        this.fileChannel = new
                RandomAccessFile(file, "rw").getChannel();

        //1. 建立内存映射
        //mappedBuffer 就代表了磁盘文件，写入它就等于写了PageCache
        this.mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE,0, MAP_SIZE);

        this.queue = new ArrayBlockingQueue<>(20000); // 加大队列
        this.ioThread = new Thread(this::ioLoop , "wal-v3-writer");
        this.ioThread.start();
    }

    public  CompletableFuture<Boolean> append(LogEntry entry){
        WriteRequest req = new WriteRequest(entry);
        if (!queue.offer(req)){
            CompletableFuture<Boolean> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("WalWriter queue is full"));
        }
        return req.future;
    }

    private void ioLoop(){
        List<WriteRequest> batch = new ArrayList<>(10000);
        while (running.get()){
            try {
                WriteRequest first = queue.poll(5, TimeUnit.MILLISECONDS);
                if (first == null){
                    // 如果空闲，也可以选择force,也可以什么都不做，依赖OS自动刷盘
                    // 为了数据安全，这里选择 periodic flush
                    // mappedBuffer.force();
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch);

                // 纯内存操作 无System Call
                for (WriteRequest req : batch){
                    if (mappedBuffer.remaining() < LogEntry.ENTRY_SIZE){
                        throw new RuntimeException("WalWriter queue is full");
                    }
                    req.entry.encode(mappedBuffer);
                }
            }catch (Exception e){
                //错误处理
                throw new RuntimeException(e);
            }
        }
    }













    @Override
    public void close() throws Exception {
        running.set(false);
        // Mmap 的释放比较麻烦，通常不推荐频繁创建销毁
        // 此处简化处理
        fileChannel.close();
    }



}
