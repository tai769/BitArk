package com.bitark.engine.wal;

import java.util.concurrent.atomic.AtomicLong;

public class LogSequencer {

    private final AtomicLong nextLeaderLsn = new AtomicLong(1);

    public long next(){
        return nextLeaderLsn.getAndIncrement();
    }

    public void recoverTo(long maxLeaderLsn){
        nextLeaderLsn.set(maxLeaderLsn + 1);
    }


}
