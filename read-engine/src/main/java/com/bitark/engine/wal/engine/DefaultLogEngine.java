package com.bitark.engine.wal.engine;

import com.bitark.commons.log.WalRecord;
import com.bitark.commons.log.WalRecordHandler;
import com.bitark.engine.WalReader.FileReadBatch;
import com.bitark.engine.adapter.WalReadBatch;
import com.bitark.engine.wal.*;
import com.bitark.engine.wal.store.WalStore;
import com.bitark.engine.wal.store.WriterResult;
import lombok.Data;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        Map.Entry<Long, WalPosition> entry = walIndex.ceiling(fromLeaderLsn);
        if (entry == null){
            return new WalReadBatch(Collections.emptyList(),entry.getKey());
        }
        if (entry.getKey() > fromLeaderLsn){
            return  new WalReadBatch(Collections.emptyList(),entry.getKey());
        }
        FileReadBatch fileBatch = walStore.readFrom(entry.getValue(), maxBytes);
        List<WalRecord> records = fileBatch.getRecords();
        if (records.isEmpty()){
            return new WalReadBatch(records,fromLeaderLsn);
        }
        WalRecord last = records.get(records.size() - 1);
        long nextLsn = last.getLeaderLsn() + 1;
        return new WalReadBatch(records,nextLsn);

    }

    @Override
    public void replay(WalRecordHandler handler) throws Exception {
        walStore.replay(handler);
    }

    @Override
    public long earliestRetainedLsn() {
        return walStore.earliestRetainedLsn();
    }
}
