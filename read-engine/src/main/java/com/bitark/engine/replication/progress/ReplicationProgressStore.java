package com.bitark.engine.replication.progress;

import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


@Slf4j
/**
 * Slave 复制进度存储。
 *
 * <p>职责：
 * 1. 保存 Slave 当前“最后一批已经完整应用成功”的复制游标
 * 2. 在 Slave 启动或下一轮 Pull 前加载 fromLsn
 *
 * <p>不负责：
 * 1. ISR 判定
 * 2. 心跳
 * 3. Full Sync 判定
 * 4. Snapshot / checkpoint
 * 5. 网络请求
 */
public class ReplicationProgressStore {

    private static final int VERSION = 1;
    private final Path path;
    public ReplicationProgressStore(Path path) {
        this.path = path;
    }

    /**
     * 持久化保存当前复制游标。
     *
     * <p>语义上保存的是：
     * “到这个 nextLsn 之前的数据，已经全部成功应用完成”。</p>
     */
    public void save(Long globalLsn)throws IOException{
        if (path.getParent() != null){
            Files.createDirectories(path.getParent());
        }
        try(DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))){
            out.writeInt(VERSION);
            out.writeLong(globalLsn);
        }
    }
    /**
     * 加载当前复制游标。
     *
     * <p>返回值会作为下一轮 Pull 的 fromLsn 使用。</p>
     */
    public Long load() throws IOException{
        if (!Files.exists(path)){
            log.error("No replication progress file found at {}", path);
            //不能返回空,会让下游出现空
            return 0L;
        }
        try(DataInputStream in = new DataInputStream(Files.newInputStream(path))){
            int version = in.readInt();
            Long lsn = in.readLong();
           return lsn;
        }
    }
}
