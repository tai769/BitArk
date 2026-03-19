package com.bitark.engine.service.command;

import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.log.LogEntry;
import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.sender.ReplicationSender;
import com.bitark.engine.wal.WalEngine;

public class ReadCommandServiceImpl implements ReadCommandService {

    private final WalEngine walEngine;
    private final ReadStatusEngine engine;
    private final ReplicationSender replicationSender;
    private final ReplicationProgressStore progressStore;

    public ReadCommandServiceImpl(WalEngine walEngine, ReadStatusEngine engine, ReplicationSender replicationSender, ReplicationProgressStore progressStore) {
        this.walEngine = walEngine;
        this.engine = engine;
        this.replicationSender = replicationSender;
        this.progressStore = progressStore;
    }
    @Override
    public void read(Long userId, Long msgId) throws Exception {
        LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId, msgId);
        Long lsn = walEngine.append(entry);
        engine.markRead(userId, msgId);
        replicationSender.sendRead(userId, msgId, lsn);
    }

    @Override
    public void readFromMaster(Long userId, Long msgId) throws Exception {
        LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId, msgId);
        walEngine.append(entry);
        engine.markRead(userId, msgId);
    }

    @Override
    public Long applyReplication(ReplicationRequest req) throws Exception {
        readFromMaster(req.getUserId(), req.getMsgId());
        Long masterLsn = req.getGlobalLsn();
        progressStore.save(masterLsn);
        return masterLsn;
    }
}
