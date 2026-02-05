package com.bitark.infrastructure.thread;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadFactoryImpl implements ThreadFactory {

    private final AtomicLong threadIndex = new AtomicLong(0);
    private final String threadNamePrefix;
    private final boolean daemon;

    public ThreadFactoryImpl(final String threadNamePrefix) {
        this(threadNamePrefix, false);
    }

    public ThreadFactoryImpl(final String threadNamePrefix, final boolean daemon) {
        this.threadNamePrefix = threadNamePrefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable r) {

        Thread thread = new Thread(r, threadNamePrefix + this.threadIndex.incrementAndGet());
        // TODO Auto-generated method stub
       
        
        thread.setUncaughtExceptionHandler((t,e) -> 
        log.error("[BUG] Thread has an uncaught exception, threadId={}, threadName={}",
            t.getId(),
            t.getName(),
            e
        )
    );
        return thread;
    }

}
