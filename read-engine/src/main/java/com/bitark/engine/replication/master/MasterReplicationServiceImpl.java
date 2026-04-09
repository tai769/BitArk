package com.bitark.engine.replication.master;

import com.bitark.commons.dto.*;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import com.bitark.engine.wal.WalEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

@Slf4j
public class MasterReplicationServiceImpl implements MasterReplicationService{

    private ReplicationTracker tracker;

    private WalEngine walEngine;

    public MasterReplicationServiceImpl(ReplicationTracker tracker, WalEngine walEngine) {
        this.tracker = tracker;
        this.walEngine = walEngine;
    }


    @Override
    public String register(ReplicationAck ack) {
        tracker.registerAck(ack.getSlaveUrl(), ack.getGlobalLsn());
        log.info("📢 Slave Registered: {} at {}", ack.getSlaveUrl(), ack.getGlobalLsn());
        return "ok";
    }

    @Override
    public void onHeartbeat(HeartBeatDTO dto) {
        if (dto == null || dto.getSlaveUrl() == null){
            return;
        }
        tracker.onHeartbeat(dto.getSlaveUrl(), dto.getGlobalLsn());
    }

    @Override
    public FetchResponse fetch(FetchRequest req) {

        //1. 参数校验
        if (req.getSlaveUrl().isEmpty() || req.getSlaveUrl().isBlank()){
            throw new IllegalArgumentException("SlaveUrl is null");
        }
        if (req.getSlaveUrl().isEmpty() || req.getSlaveUrl().isBlank()){
            throw new IllegalArgumentException("MaxBytes is null");
        }
        if (req.getSlaveUrl().isEmpty() || req.getSlaveUrl().isBlank()){
            throw new IllegalArgumentException("FromLsn is null");
        }

        //2. 更新Slave当前进度 / 存活状态
        tracker.registerAck(req.getSlaveUrl(), req.getFromLsn());
        tracker.onHeartbeat(req.getSlaveUrl(), req.getFromLsn());

        //3. 判断是否断代
        long earliest = walEngine.earliestRetainedLsn();

        FetchResponse resp = new FetchResponse();
        if (req.getFromLsn() < earliest){
            resp.setStatus(FetchStatus.NEED_FULL_SYNC);
            resp.setNextLsn(req.getFromLsn());
            resp.setEntries(Collections.emptyList());
            return resp;
        }
        //4. 先返回空脾气,下一步在补readFrom
        resp.setStatus(FetchStatus.OK);
        resp.setNextLsn(req.getFromLsn());
        resp.setEntries(Collections.emptyList());
        return resp;

    }
}
