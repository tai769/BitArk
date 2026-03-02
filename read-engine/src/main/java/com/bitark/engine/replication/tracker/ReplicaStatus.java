package com.bitark.engine.replication.tracker;

public enum ReplicaStatus {
    OBSERVER,       // 刚注册/重连，正在追赶，不参与 GC 计算
    ISR,            // 活跃且同步，参与 getMinIsrAckLsn() 计算
    OUT_OF_SYNC     // 活着但落后太多，不参与 GC 计算



}
