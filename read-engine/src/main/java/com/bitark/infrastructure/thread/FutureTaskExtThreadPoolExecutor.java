package com.bitark.infrastructure.thread;

import java.util.concurrent.*;

public class FutureTaskExtThreadPoolExecutor extends ThreadPoolExecutor {

    public FutureTaskExtThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                           TimeUnit unit,
                                           BlockingQueue<Runnable> workQueue,
                                           ThreadFactory threadFactory,
                                           RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {
        return new FutureTaskExt<>(runnable, value);
    }
}
