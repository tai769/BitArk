package com.bitark.adapter.controller;

import com.bitark.commons.dto.HeartBeatDTO;
import com.bitark.engine.replication.master.MasterReplicationService;
import com.bitark.engine.replication.slave.SlaveReplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;

/*
* 内部同步接口
* 不对外公网开放,仅用于集群内部 Master -> Slave的数据复制
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



    @PostMapping("/heartbeat")
    public String heartbeat(@RequestBody HeartBeatDTO dto) {
        masterReplicationService.onHeartbeat(dto);
        return "ok";
    }
}
