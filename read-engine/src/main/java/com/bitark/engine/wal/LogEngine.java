package com.bitark.engine.wal;

import com.bitark.commons.log.WalRecord;
import com.bitark.commons.log.WalRecordHandler;
import com.bitark.engine.adapter.WalReadBatch;

public interface LogEngine {
    AppendResult appendAsLeader(WalRecord record);

    AppendResult appendReplicated(WalRecord record);

    WalReadBatch readBatch(long fromLeaderLsn, int maxBytes) throws Exception;

    void replay(WalRecordHandler handler) throws Exception;

    long earliestRetainedLsn();
}
