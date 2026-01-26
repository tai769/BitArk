package com.bitark.engine.checkpoint;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.bitark.commons.wal.WalCheckpoint;

public class CheckpointManager {

    private static final int VERSION = 1;

    private final Path checkpointPath;

    public CheckpointManager(Path checkpointPath) {
        this.checkpointPath = checkpointPath;
    }

    //写入 version+segmentIndex + segmentoffset
    public void save(WalCheckpoint pos) throws IOException{
        try(DataOutputStream out = new DataOutputStream(Files.newOutputStream(checkpointPath))){
            out.writeInt(VERSION);
            out.writeInt(pos.getSegmentIndex());
            out.writeLong(pos.getSegmentOffset());
        }
    }

    //读取: 按照同样的顺序读取回来
    public WalCheckpoint load() throws IOException{
        try(DataInputStream in = new DataInputStream(Files.newInputStream(checkpointPath))){
            int version = in.readInt();
            int segmentIndex = in.readInt();
            long segmentOffset = in.readLong();
            return new WalCheckpoint(version, segmentIndex, segmentOffset);                                                                                                                                                                                                                                                                                                                                                                                                         
        }
    }

}
