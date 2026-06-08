package com.bitark.engine.service.command;

import com.bitark.commons.command.ReadMarkCommand;
import com.bitark.commons.command.ReadMarkCommandCodec;
import com.bitark.commons.dto.FetchEntryDTO;
import com.bitark.commons.dto.FetchResponse;
import com.bitark.commons.enums.CommandTypes;
import com.bitark.commons.log.WalRecord;
import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.sender.ReplicationSender;
import com.bitark.engine.service.apply.ReadStateMachineApplier;
import com.bitark.engine.wal.AppendResult;
import com.bitark.engine.wal.LogEngine;
import lombok.extern.slf4j.Slf4j;


import java.util.List;

/**
 * 写路径命令服务实现。
 *
 * <p>职责边界：
 * 1. 把已读变更落到本地 WAL
 * 2. 更新本地内存状态
 * 3. 在复制场景下推进复制进度
 *
 * <p>它不负责：
 * 1. HTTP 通信
 * 2. 定时调度
 * 3. 主从角色判断
 */
@Slf4j
public class ReadCommandServiceImpl implements ReadCommandService {

    private final ReplicationProgressStore progressStore;

    private final ReadStateMachineApplier applier;

    private final LogEngine logEngine;

    public ReadCommandServiceImpl( ReadStateMachineApplier applier,
                                   LogEngine logEngine,
                                   ReplicationProgressStore store) {

        this.logEngine = logEngine;
        this.progressStore = store;
        this.applier = applier;
    }

    /**
     * Master 本地业务写入口。
     *
     * <p>当前系统已切到 Pull 主链，因此这里仅负责本地落地：
     * 1. 追加 WAL
     * 2. 更新内存位图
     *
     * <p>旧 Push 时代的主动发送逻辑已停用，复制由 Slave 主动 fetch 完成。
     */
    @Override
    public void read(Long userId, Long msgId) throws Exception {

        ReadMarkCommand command = new ReadMarkCommand(userId,msgId);
        byte[] payload = ReadMarkCommandCodec.encode(command);

        WalRecord record = WalRecord.create(CommandTypes.READ_MARK, payload);

        logEngine.appendAsLeader(record);


        applier.apply(record);



    }

    /**
     * Slave 应用上游复制数据时的本地落地入口。
     *
     * <p>这里只做本地状态变更，不推进外部复制，不做网络发送。
     */
    @Override
    public void applyReplicatedRead(WalRecord record) throws Exception {
        logEngine.appendReplicated(record);

        applier.apply(record);


    }

    /**
     * Pull 模式下的批量应用方法。
     *
     * <p>关键边界：
     * 因为这是“复制数据落地”，不是“本地业务写入”。两者职责必须分开。
     */
    public void applyFetch(List<FetchEntryDTO> entryDTOList) throws Exception {
        for (FetchEntryDTO fetchEntry : entryDTOList){
            WalRecord record = new WalRecord(fetchEntry.getLeaderLsn(),
                    fetchEntry.getEpoch(),
                    fetchEntry.getType(),
                    fetchEntry.getPayload());
            this.applyReplicatedRead(record);
        }
    }


    /**
     * Pull 模式下的批量复制应用入口。
     *
     * <p>关键边界：
     * 只有当整批 entries 全部成功应用后，才能保存 nextLsn。
     * 否则一旦中途失败却提前推进游标，会导致 Slave 跳过尚未真正落地的数据。
     */
    @Override
    public void applyFetchBatch(FetchResponse fetchResponse) throws Exception {
        applyFetch(fetchResponse.getEntries());
        progressStore.save(fetchResponse.getNextLsn());
    }
}
