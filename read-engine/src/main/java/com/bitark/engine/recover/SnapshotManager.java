package com.bitark.engine.recover;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.bitark.engine.ReadStatusEngine;

/**
 * 快照二进制格式管理器。
 *
 * <p>职责：
 * 1. 将 ReadStatusEngine 序列化到本地 snapshot 文件
 * 2. 从本地 snapshot 文件恢复 ReadStatusEngine
 * 3. 将 ReadStatusEngine 序列化成内存 byte[]，供 Full Sync 通过网络传输</p>
     *
 * <p>它不负责：
 * 1. 决定什么时候生成快照
 * 2. 决定快照对应哪个 WAL LSN
 * 3. 处理 Master/Slave 复制协议</p>
 */
public class SnapshotManager {

    private final Path snapshotPath;

    public SnapshotManager(Path snapshotPath) {
        this.snapshotPath = snapshotPath;
    }

    public void save(ReadStatusEngine engine) throws IOException {
        try(DataOutputStream out = new DataOutputStream(Files.newOutputStream(snapshotPath))) {
            engine.saveSnapshot(out);
        }
    }
    public void load(ReadStatusEngine engine) throws IOException{
        try(DataInputStream in = new DataInputStream(Files.newInputStream(snapshotPath))) {
            engine.loadSnapshot(in);
        } 
    }

    /**
     * 将当前内存状态导出为 Full Sync 可传输的二进制快照。
     *
     * <p>这里复用 ReadStatusEngine 已有的 saveSnapshot(DataOutputStream) 格式，
     * 只是把输出目标从文件换成内存字节数组。</p>
     */
    public byte[] dumpBytes(ReadStatusEngine engine) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            engine.saveSnapshot(out);
            out.flush();
            return bos.toByteArray();
        }
    }

}
