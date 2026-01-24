package com.bitark.infrastructure.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bitark.infrastructure.thread.ThreadUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class InfrastructureConfig {


    private ExecutorService syncExecutor;

    @Bean
    public ExecutorService syncExecutor() {
        this.syncExecutor = ThreadUtils.newThreadPoolExecutor(
                32, 64, 1000L, TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<>(),
                "sync-pool",
                true);
        return this.syncExecutor;
    }


    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }




    @PreDestroy
    public void shutdownExecutors() {
        // 容器关闭前执行
        ThreadUtils.shutdownGracefully(syncExecutor, 10, TimeUnit.SECONDS);
    }

}
