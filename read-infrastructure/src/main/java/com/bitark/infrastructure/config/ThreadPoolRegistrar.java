package com.bitark.infrastructure.config;

import com.bitark.infrastructure.thread.ThreadUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class ThreadPoolRegistrar {

    private final ThreadPoolManager threadPoolManager;

    public ThreadPoolRegistrar(ThreadPoolManager threadPoolManager) {
        this.threadPoolManager = threadPoolManager;

    }

    @PostConstruct
    public void init() {
        registerAllPools();
    }



    private void registerAllPools() {


        // 注册复制线程池
        registerIfAbsent("replication", "sync-pool",
                () -> ThreadUtils.newThreadPoolExecutor(
                        32, 64, 1000L, TimeUnit.MILLISECONDS,
                        new LinkedBlockingDeque<>(),
                        "replication-sync",
                        true
                ), 30L);

        // 注册心跳线程池
        registerIfAbsent("cluster", "heartbeat-pool",
                () -> ThreadUtils.newSingleThreadScheduledExecutor("cluster-heartbeat", true), 10L);

        // 注册存活检测线程池
        registerIfAbsent("cluster", "liveness-pool",
                () -> ThreadUtils.newSingleThreadScheduledExecutor("cluster-liveness", true), 15L);
    }


    private void registerIfAbsent(String domain,
                                  String name,
                                  Supplier<ExecutorService> creator, long timeout) {
        if (!threadPoolManager.exists(domain, name)){
            threadPoolManager.register(domain, name, creator.get(), timeout);
        }
    }

}
