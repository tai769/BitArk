package com.bitark.adapter.controller;

import com.bitark.engine.config.ReplicationConfig;
import com.bitark.engine.replication.ReplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.engine.service.ReadService;

/*
* 内部同步接口
* 不对外公网开放,仅用于集群内部 Master -> Slave的数据复制
*/
@Slf4j
@RestController
@RequestMapping("/internal")
public class InternalSyncController {

    @Resource
    private ReplicationService  replicationService;


    @PostMapping("/sync")
    public ReplicationAck sync(@RequestBody ReplicationRequest req)throws Exception {
        ReplicationAck ack = replicationService.sync( req);
        return ack;
    }

    @PostMapping("/register")
    public String register(@RequestBody ReplicationAck ack){
        replicationService.register(ack);
         log.info("📢 Slave Registered: {} at {}", ack.getSlaveUrl(), ack.toLsnPosition());
         return "ok";
    }

    @PostMapping("/heartbeat")
    public String heartbeat(@RequestBody ReplicationAck ack){

        log.info("📢 Slave Heartbeat: {} at {}", ack.getSlaveUrl(), ack.toLsnPosition());
        return "ok";
    }
}
