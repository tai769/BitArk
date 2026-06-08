package com.bitark.engine.wal.engine;

import com.bitark.commons.log.WalRecord;
import com.bitark.commons.log.WalRecordHandler;
import com.bitark.engine.adapter.WalReadBatch;
import com.bitark.engine.wal.AppendResult;
import com.bitark.engine.wal.LogEngine;
import com.bitark.engine.wal.LogSequencer;
import com.bitark.engine.wal.WalIndex;
import com.bitark.engine.wal.store.WalStore;
import com.bitark.engine.wal.store.WriterResult;
import lombok.Data;

@Data
public class DefaultLogEngine implements LogEngine {

    private final int currentEpoch = 1;


    private LogSequencer sequencer;
    private WalStore walStore;
    private WalIndex walIndex;

    public DefaultLogEngine(LogSequencer sequencer, WalStore walStore, WalIndex walIndex){
        this.sequencer = sequencer;
        this.walIndex = walIndex;
        this.walStore = walStore;
    }

    @Override
    public AppendResult appendAsLeader(WalRecord record) {
        if (record.hasLeaderLsn()){
            throw new IllegalArgumentException("leader append record should not have leaderLsn");
        }
        long leaderLsn = sequencer.next();

        record.setLeaderLsn(leaderLsn);
        record.setEpoch(currentEpoch);

        WriterResult write = walStore.append(record);

        walIndex.put(leaderLsn, write.getPosition());

        return new AppendResult(leaderLsn, record.getEpoch(), write.getPosition());

    }

    @Override
    public AppendResult appendReplicated(WalRecord record) {
        if (!record.hasLeaderLsn()){
            throw new IllegalArgumentException("replicated record must have leaderLsn");
        }
        WriterResult writer = walStore.append(record);

        walIndex.put(record.getLeaderLsn(), writer.getPosition());

        return new AppendResult(record.getLeaderLsn(), record.getEpoch(), writer.getPosition());
    }

    @Override
    public WalReadBatch readBatch(long fromLeaderLsn, int maxBytes) throws Exception {
        return null;
    }

    @Override
    public void replay(WalRecordHandler handler) throws Exception {

    }

    @Override
    public long earliestRetainedLsn() {
        return 0;
    }
}
