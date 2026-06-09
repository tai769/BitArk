package com.bitark.engine.replication.master;

import com.bitark.commons.command.ReadMarkCommand;
import com.bitark.commons.command.ReadMarkCommandCodec;
import com.bitark.commons.dto.*;
import com.bitark.commons.log.LogEntry;
import com.bitark.commons.log.WalRecord;
import com.bitark.engine.adapter.WalReadBatch;
import com.bitark.engine.replication.tracker.ReplicationTracker;
import com.bitark.engine.wal.LogEngine;
import com.bitark.engine.wal.WalEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Master 侧复制编排实现。
 *
 * <p>它把复制请求转给两个核心组件：
 * 1. ReplicationTracker：记录 Slave 进度、心跳和 ISR 状态
 * 2. WalEngine：根据 globalLsn 读取增量 WAL，或提供 Full Sync 所需的快照边界</p>
 */
@Slf4j
public class MasterReplicationServiceImpl implements MasterReplicationService{

    private ReplicationTracker tracker;

    private WalEngine walEngine;

    private LogEngine logEngine;

    public MasterReplicationServiceImpl(ReplicationTracker tracker, WalEngine walEngine, LogEngine logEngine) {
        this.tracker = tracker;
        this.logEngine = logEngine;
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

    /**
     * Pull 增量读取主入口。
     *
     * <p>关键边界：
     * 如果 fromLsn 早于 earliestRetainedLsn，说明 Slave 需要的 WAL 已经被 GC，
     * 这时不能继续返回增量，必须返回 NEED_FULL_SYNC。</p>
     */
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
        long earliest = logEngine.earliestRetainedLsn();

        FetchResponse resp = new FetchResponse();
        if (req.getFromLsn() < earliest){
            resp.setStatus(FetchStatus.NEED_FULL_SYNC);
            resp.setNextLsn(req.getFromLsn());
            resp.setEntries(Collections.emptyList());
            return resp;
        }
        //4. Pull 模式下，读取一批 WAL 并转换成批量返回 DTO
        WalReadBatch batch = logEngine.readBatch(req.getFromLsn(), req.getMaxBytes());
        List<FetchEntryDTO> entries = new ArrayList<>();
        for (WalRecord record :  batch.getRecords()){
            FetchEntryDTO item = new FetchEntryDTO();
            item.setLeaderLsn(record.getLeaderLsn());
            item.setPayload(record.getPayload());
            item.setType(record.getType());
            item.setEpoch(record.getEpoch());
            entries.add(item);
        }
        resp.setStatus(FetchStatus.OK);
        resp.setEntries(entries);
        resp.setNextLsn(batch.getNextLsn());
        return resp;

    }

    /**
     * Full Sync 全量同步主入口。
     *
     * <p>当前阶段先只建立协议入口。真正实现时，这里需要生成：
     * 1. snapshotBytes：Master 当前完整状态
     * 2. snapshotLsn：这份状态对应的全局 LSN</p>
     */
    @Override
    public FullSyncResponse fullSync(FullSyncRequest req) throws Exception {
        if (req == null){
            throw  new IllegalArgumentException("Request is null");
        }
        if (req.getSlaveUrl() == null || req.getSlaveUrl().isBlank()){
            throw new IllegalArgumentException("SlaveUrl is null");
        }
        if (req.getCurrentLsn() == null || req.getCurrentLsn() < 0){
            throw new IllegalArgumentException("CurrentLsn is invalid");
        }
        return null;
    }
}
