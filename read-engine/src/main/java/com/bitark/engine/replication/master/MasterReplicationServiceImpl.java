package com.bitark.engine.replication.master;

import com.bitark.commons.dto.*;
import com.bitark.commons.log.LogEntry;
import com.bitark.engine.adapter.WalReadBatch;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import com.bitark.engine.wal.WalEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public FetchResponse fetch(FetchRequest req) throws Exception {

        //1. 参数校验
        if (req == null) {
            throw new IllegalArgumentException("FetchRequest is null");
        }
        if (req.getSlaveUrl() == null || req.getSlaveUrl().isBlank()) {
            throw new IllegalArgumentException("SlaveUrl is null");
        }
        if (req.getMaxBytes() == null || req.getMaxBytes() <= 0) {
            throw new IllegalArgumentException("MaxBytes is invalid");
        }
        if (req.getFromLsn() == null || req.getFromLsn() < 0) {
            throw new IllegalArgumentException("FromLsn is invalid");
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
        //4. Pull 模式下，读取一批 WAL 并转换成批量返回 DTO
        WalReadBatch batch = walEngine.readBatch(req.getFromLsn(), req.getMaxBytes());
        List<FetchEntryDTO> entries = new ArrayList<>();
        for (LogEntry entry :  batch.getEntries()){
            FetchEntryDTO item = new FetchEntryDTO();
            item.setUserId(entry.getUserId());
            item.setMsgId(entry.getMsgId());
            entries.add(item);
        }
        resp.setStatus(FetchStatus.OK);
        resp.setEntries(entries);
        resp.setNextLsn(batch.getNextLsn());
        return resp;

    }
}
