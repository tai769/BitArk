package com.bitark.engine.replication.reporter;

public interface ReplicationReporter {
    void reportStartup(Long globalLsn);
}
