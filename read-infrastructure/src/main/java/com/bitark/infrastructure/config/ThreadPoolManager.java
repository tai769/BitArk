package com.bitark.infrastructure.config;

import com.bitark.infrastructure.thread.ThreadUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ThreadPoolManager {

    private Map<String, Map<String, PoolMeta>> resourceManager
            = new ConcurrentHashMap<>();


    public boolean exists(String domain, String name){
        Map<String, PoolMeta> domainMap = resourceManager.get(domain);
        return domainMap != null && domainMap.containsKey(name);
    }

    public void  register(String domain, String name,
                          ExecutorService executor, long timeoutSec){
        //1. 获取或创建第一层
        Map<String, PoolMeta> domainMap = resourceManager.computeIfAbsent(domain,
                k -> new ConcurrentHashMap<>());

        //2. 检查重复注册
        if (exists(domain, name)){
            log.warn("Pool already registered: {}", name);
            return; // 这里可以抛出异常
        }

        //3. 包装并且存入
        PoolMeta meta = new PoolMeta(executor, domain, name, timeoutSec);
        domainMap.put(name, meta);
        log.info("Pool registered: {}", meta);
    }

    /*
     * 获取线程池
     */
    public ExecutorService get(String domain, String name){
        Map<String, PoolMeta> domainMap = resourceManager.get(domain);
        if (domainMap == null){
            return null;
        }
        PoolMeta meta = domainMap.get(name);
        return meta != null ? meta.getExecutor() : null ;
    }

    @PreDestroy
    public void shutdownAll(){
        log.info("Shutting down all thread pools");
        resourceManager.forEach((domain, poolMap) -> {
            log.info("Shutting down pool: {}", domain);
            poolMap.forEach((name, meta) -> shutdownPool(meta));
        });
        log.info("Shutdown completed for all pools");

    }

    private void shutdownPool(PoolMeta meta) {
        try{
            ExecutorService es = meta.getExecutor();
            if (es.isShutdown()){
                return;
            }
            log.info("Shutting down pool: {}, timeout: {}", meta, meta.getShutdownTimeout());
            ThreadUtils.shutdownGracefully(es, meta.getShutdownTimeout(), TimeUnit.SECONDS);
            log.info("Shutdown completed for pool: {}", meta);
        }catch (Exception e){
            log.error("Error shutting down pool: {}", meta, e);
        }
    }
}
