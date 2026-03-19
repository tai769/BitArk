package com.bitark.engine.replication.bootstrap;

import com.bitark.engine.replication.progress.ReplicationProgressStore;
import com.bitark.engine.replication.reporter.ReplicationReporter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReplicationBootstrapper {

    private final ReplicationProgressStore progressStore;
    private final ReplicationReporter reporter;

    public ReplicationBootstrapper(ReplicationProgressStore progressStore, ReplicationReporter reporter) {
        this.progressStore = progressStore;
        this.reporter = reporter;
    }

    public void reportIfPresent() {
        try {
            Long globalLsn = progressStore.load();
            if (globalLsn != null) {
                reporter.reportStartup(globalLsn);
            }
        } catch (Exception e) {
            log.error("加载 replication progress 失败，跳过注册上报", e);
        }
    }
}
