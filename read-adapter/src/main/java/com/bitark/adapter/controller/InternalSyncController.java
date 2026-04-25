package com.bitark.adapter.controller;

import com.bitark.commons.dto.*;
import com.bitark.engine.replication.master.MasterReplicationService;
import com.bitark.engine.replication.slave.SlaveReplicationService;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * 集群内部复制接口。
 *
 * 职责：
 * 1. 接收 Slave 的注册、心跳、Pull 增量拉取和 Full Sync 请求
 * 2. 把 HTTP 请求转交给 engine 层的复制服务
 *
 * 不负责：
 * 1. WAL 读取
 * 2. 状态落地
 * 3. 复制状态机判断
 */
@Slf4j
@RestController
@RequestMapping("/internal")
public class InternalSyncController {

    @Resource
    private MasterReplicationService masterReplicationService;

    @Resource
    private SlaveReplicationService slaveReplicationService;


    @PostMapping("/sync")
    public ReplicationAck sync(@RequestBody ReplicationRequest req)throws Exception {
        return slaveReplicationService.sync( req);
    }

    @PostMapping("/register")
    public String register(@RequestBody ReplicationAck ack){
        return masterReplicationService.register(ack);

    }

    @SneakyThrows
    @PostMapping("/fetch")
    public FetchResponse fetch(@RequestBody FetchRequest req){
        return  masterReplicationService.fetch(req);
    }


    @PostMapping("/heartbeat")
    public String heartbeat(@RequestBody HeartBeatDTO dto) {
        masterReplicationService.onHeartbeat(dto);
        return "ok";
    }

    @PostMapping("/full-sync")
    /*
     * Full Sync 入口。
     *
     * 当 Slave 发现增量 WAL 已经断代时，会调用该接口向 Master 请求完整快照。
     */
    public FullSyncResponse fullSync(@RequestBody FullSyncRequest req) throws Exception {
        return masterReplicationService.fullSync(req);
    }
}
