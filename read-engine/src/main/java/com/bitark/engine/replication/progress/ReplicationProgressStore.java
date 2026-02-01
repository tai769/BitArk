package com.bitark.engine.replication.progress;

import com.bitark.commons.lsn.LsnPosition;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;



public class ReplicationProgressStore {

    private static final int VERSION = 1;
    private final Path path;
    public ReplicationProgressStore(Path path) {
        this.path = path;
    }



    public void save(LsnPosition lsn)throws IOException{
        if (path.getParent() != null){
            Files.createDirectories(path.getParent());
        }
        try(DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))){
            out.writeInt(VERSION);
            out.writeInt(lsn.getSegmentIndex());
            out.writeLong(lsn.getOffset());
        }
    }

    public LsnPosition load() throws IOException{
        if (!Files.exists(path)){
            return null;
        }
        try(DataInputStream in = new DataInputStream(Files.newInputStream(path))){
            int version = in.readInt();
            int seg = in.readInt();
            Long off = in.readLong();
            return new LsnPosition(seg, off);
        }
    }
}
