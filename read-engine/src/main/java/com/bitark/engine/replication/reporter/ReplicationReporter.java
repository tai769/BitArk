package com.bitark.engine.replication.reporter;

import com.bitark.commons.lsn.LsnPosition;
import org.springframework.stereotype.Service;


public interface ReplicationReporter {
    void reportStartup(LsnPosition lsn);
}
