package com.bitark.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 将 ThreadPoolManager 中已注册的线程池暴露为 Spring Bean，
 * 使上层模块（如 read-engine）可通过 @Qualifier 注入标准接口，
 * 无需直接依赖 ThreadPoolManager。
 */
@Configuration
public class ExecutorBeans {

    private final ThreadPoolManager manager;

    /**
     * 注入 ThreadPoolRegistrar 以保证初始化顺序：
     * Registrar 的 @PostConstruct 先完成注册，此处的 @Bean 方法才能取到线程池。
     */
    public ExecutorBeans(ThreadPoolManager manager, ThreadPoolRegistrar registrar) {
        this.manager = manager;
    }

    @Bean("heartbeatScheduler")
    public ScheduledExecutorService heartbeatScheduler() {
        return (ScheduledExecutorService) manager.get("cluster", "heartbeat-pool");
    }

    @Bean("livenessScheduler")
    public ScheduledExecutorService livenessScheduler() {
        return (ScheduledExecutorService) manager.get("cluster", "liveness-pool");
    }

    @Bean("replicationSyncExecutor")
    public ExecutorService replicationSyncExecutor() {
        return manager.get("replication", "sync-pool");
    }
}
