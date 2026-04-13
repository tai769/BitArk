package com.bitark.engine.replication.slave;

import com.bitark.commons.dto.FetchResponse;
import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.engine.replication.config.ReplicationConfig;
import com.bitark.engine.service.command.ReadCommandService;

/**
 * Slave 侧复制执行器。
 *
 * <p>职责边界：
 * 1. 兼容旧 Push 模式的单条复制执行
 * 2. 承接新 Pull 模式的批量复制落地
 *
 * <p>它不负责：
 * 1. 定时拉取调度
 * 2. HTTP 请求发起
 * 3. 复制进度文件的底层存储实现
 */
public class SlaveReplicationServiceImpl implements SlaveReplicationService{

    private final ReadCommandService readCommandService;
    private final ReplicationConfig replicationConfig;

    public SlaveReplicationServiceImpl(ReadCommandService readCommandService, ReplicationConfig replicationConfig) {
        this.readCommandService = readCommandService;
        this.replicationConfig = replicationConfig;
    }

    /**
     * 旧 Push 模式下的单条复制执行入口。
     */
    @Override
    public ReplicationAck sync(ReplicationRequest req) throws Exception {
        Long globalLsn = readCommandService.applyReplication(req);
        ReplicationAck ack = new ReplicationAck();
        ack.setSlaveUrl(replicationConfig.getSelfUrl());   // 只能由本机填写
        ack.setGlobalLsn(globalLsn);
        return ack;
    }

    /**
     * Pull 模式下的批量复制执行入口。
     *
     * <p>这里只负责把一批数据交给命令服务落地，
     * 具体的 WAL 追加、内存应用和进度推进都在 ReadCommandService 内部完成。
     */
    public void applyFetchBatch(FetchResponse response)throws Exception{
        readCommandService.applyFetchBatch(response);
    }
}
