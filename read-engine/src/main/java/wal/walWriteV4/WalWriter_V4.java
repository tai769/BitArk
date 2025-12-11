package wal.walWriteV4;


import wal.walWriteV4.entry.LogEntry;
import wal.walWriteV4.segment.Segment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
/**
 * @author: ning
 * @date: 2025/12/11
 * @Description: wal日志_v4 1.事实上还存在无限队列可能导致导致的oom
 *                         2.segment切换旧板segment没有及时落盘
 *                         3.三种写入模式还未明确
 *                         4.异常处理其实没有很严谨
 */
public class WalWriter_V4  implements AutoCloseable{
    //配置参数
    private static final long DEFAULT_SEGMENT_SIZE = 1024L * 1024L * 128L;
    //刷盘间隔
    private static final long DEFAULT_FORCE_INTERVAL_MS = 100;
    //最大待写入的entry队列长度
    //private static final int DEFAULT_MAX_PENDING = 20000;

    private final Path walDir;
    //可配置的segment大小
    private final long segmentSize;
    //当前segment
    private volatile Segment currentSegment;
    //写偏移量
    private final AtomicLong writeOffset = new AtomicLong(0);
    //待写入的entry队列
    private final ConcurrentLinkedQueue<PendingEntry> pending = new ConcurrentLinkedQueue<>();
    private final Thread ioThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    //可配置的刷盘间隔
    private final long forceIntervalMs;
    @Override
    public void close() throws Exception {
        if (!running.getAndSet( false)){
            return;
        }
        ioThread.interrupt();
        ioThread.join(2000);
        try{
            currentSegment.mmap.force();
        }catch (Throwable t){
            t.printStackTrace();
        }try {
            currentSegment.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    //待写入的entry队列节点
    private static class PendingEntry{
        final long startOffset;
        final int length;
        final CompletableFuture<Boolean> future;
        final WriteMode mode;
        PendingEntry(long startOffset, int length, CompletableFuture<Boolean> future, WriteMode mode){
            this.startOffset = startOffset;
            this.length = length;
            this.future = future;
            this.mode = mode;
        }
        long getEndOffset(){
            return startOffset + length;
        }
    }
    public WalWriter_V4(Path walDir) throws IOException {
        this(walDir, DEFAULT_SEGMENT_SIZE, DEFAULT_FORCE_INTERVAL_MS);
    }
    public WalWriter_V4(Path walDir, long segmentSize, long forceIntervalMs) throws IOException {
        this.walDir = walDir;
        this.segmentSize = segmentSize;
        this.forceIntervalMs = forceIntervalMs;
        //如果wal目录不存在则创建目录
        if(!walDir.toFile().exists()){
            walDir.toFile().mkdirs();
        }
        this.currentSegment = new Segment(walDir.resolve("wal.log"), 0, segmentSize);
        this.writeOffset.set(currentSegment.baseOffset);
        this.ioThread = new Thread(this::ioLoop, "wal-v4-writer");
        this.ioThread.setDaemon(true);
        this.ioThread.start();
    }

    private void ioLoop() {
        List<PendingEntry> batch = new ArrayList<>();
        try {
            while (running.get()) {
                long loopStart = System.currentTimeMillis();
                if(pending.isEmpty()){
                    Thread.sleep(forceIntervalMs);
                    continue;
                }
                PendingEntry head;
                long maxEnd = -1;
                while ((head = pending.poll()) != null){
                    batch.add(head);
                    if (head.getEndOffset() > maxEnd){
                        maxEnd = head.getEndOffset();
                    }
                    if(!batch.isEmpty()){
                        try{
                            currentSegment.mmap.force();
                        } catch (Throwable t) {
                            for(PendingEntry e : batch){
                                e.future.completeExceptionally(t);
                            }
                            batch.clear();
                            running.set(false);
                            break;
                        }
                        for(PendingEntry e : batch){
                            if(e.mode == WriteMode.ASYNC){
                                e.future.complete(true);
                            }else if(e.mode == WriteMode.SYNC){
                                e.future.complete(true);
                            }else {
                                e.future.complete(true);
                            }
                        }
                        batch.clear();
                    }
                    long used = System.currentTimeMillis() - loopStart;
                    if (used < forceIntervalMs){
                        Thread.sleep(Math.max(forceIntervalMs - used, 1));
                    }
                }
            }

        }  catch (InterruptedException e){
            running.set(false);
        }catch (Throwable t) {
            PendingEntry e;
            while ((e = pending.poll()) != null){
                e.future.completeExceptionally(t);
            }
        }
    }

    public CompletableFuture<Boolean> append(LogEntry entry){
        return append(entry, WriteMode.ASYNC);
    }
    public CompletableFuture<Boolean> append(LogEntry entry, WriteMode mode){
        if (!running.get()){
            CompletableFuture<Boolean> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException("WalWriter is closed"));
            return f;
        }
        int len = entry.size();
        long startOffset = writeOffset.getAndAdd(len);
        ensureSegmnent(startOffset,len);
        try {
            int segIndex = (int) (startOffset - currentSegment.baseOffset);
            ByteBuffer buf = currentSegment.mmap.duplicate();
            buf.position(segIndex);
            entry.encode(buf);
        } catch (Exception e) {
            CompletableFuture<Boolean> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        PendingEntry p = new PendingEntry(startOffset, len, future, mode);
        pending.add(p);
        if (mode == WriteMode.SYNC){
            return future;
        }
        return future;
    }

    private synchronized void ensureSegmnent(long startOffset, int len) {
        long segBase = currentSegment.baseOffset;
        long segEnd = segBase + currentSegment.size;
        long endOffset = startOffset + len;
        if(endOffset > segEnd){
            long newBase = (startOffset / segmentSize + 1) * segmentSize;
            try {
                Segment s = createSegment(newBase);
                Segment old = currentSegment;
                this.currentSegment = s;
                try {
                    old.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Segment createSegment(long newBase) throws IOException {
        String name = String.format("wal.log.%d", newBase);
        Path path = walDir.resolve(name);
        return new Segment(path, newBase, segmentSize);
    }
    //内部类 定义LogEntry写入模式，目前支持同步、批量同步、异步三种模式
    public enum WriteMode{
        SYNC,BATCH_SYNC,ASYNC
    }

}
