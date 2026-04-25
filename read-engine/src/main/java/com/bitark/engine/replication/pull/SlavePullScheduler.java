package com.bitark.engine.replication.pull;

import com.bitark.commons.dto.FetchRequest;
import com.bitark.commons.dto.FetchResponse;
import com.bitark.commons.dto.FetchStatus;
import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.slave.SlaveReplicationService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Slave 侧 Pull 调度器。
 *
 * <p>职责：
 * 1. 定时读取本地复制进度 fromLsn
 * 2. 通过 HTTP 调用 Master 的 /internal/fetch 接口
 * 3. 按响应状态分发：
 *    - OK -> 交给 Slave 复制执行器落地
 *    - NEED_FULL_SYNC -> 进入全量同步分支（当前阶段先保留骨架）
 *
 * <p>不负责：
 * 1. 本地 WAL 落地
 * 2. 本地内存位图更新
 * 3. Full Sync 具体实现细节
 */
public class SlavePullScheduler {

    private ReplicationConfig replicationConfig;

    private ReplicationProgressStore replicationProgressStore;

    private RestTemplate restTemplate;

    private SlaveReplicationService slaveReplicationService;

    private ScheduledExecutorService executorService;



    public SlavePullScheduler(ReplicationConfig replicationConfig, ReplicationProgressStore replicationProgressStore, RestTemplate restTemplate, SlaveReplicationService slaveReplicationService,
                              @Qualifier("pullScheduler")ScheduledExecutorService executorService) {
        this.replicationConfig = replicationConfig;
        this.replicationProgressStore = replicationProgressStore;
        this.restTemplate = restTemplate;
        this.slaveReplicationService = slaveReplicationService;
        this.executorService = executorService;
    }




    public void start(){
        executorService.scheduleAtFixedRate(() -> {
            // 调度器只负责周期性触发一次拉取，不直接处理批量落地细节。
            try {
                this.pullOnce();
            } catch (Exception e) {
                e.printStackTrace();
                //打印日志,但是还是要保证下一轮正常调度
            }
        }, replicationConfig.getFetchIntervalMs(), replicationConfig.getFetchIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void pullOnce() throws Exception {
        // 1. 读取本地复制游标，决定本次从哪里开始拉
        Long localLsn = replicationProgressStore.load();

        // 2. 通过 HTTP 调 Master 的 /internal/fetch，而不是直接调用 Master 本地 Service 对象
        FetchRequest req = new FetchRequest(replicationConfig.getSelfUrl(), localLsn, replicationConfig.getFetchBatchBytes());
        FetchResponse resp = restTemplate.postForObject(replicationConfig.getMasterUrl() + "/internal/fetch", req, FetchResponse.class);
        // 3. 按响应状态分发处理
        handleResponse(resp);
    }

    private void handleResponse(FetchResponse resp) throws Exception {
        if (resp == null){
            throw  new IllegalArgumentException("Response is null");
        }
        if (resp.getStatus() == FetchStatus.OK){
            // 增量 Pull 成功时，调度器只负责把结果转交给复制执行器。
            slaveReplicationService.applyFetchBatch(resp);
        }else {
            // Full Sync 是另一条链路。当前阶段先保留骨架，不和增量 Pull 混在一起实现。
            slaveReplicationService.handleNeedFullSync();
        }
    }
}
