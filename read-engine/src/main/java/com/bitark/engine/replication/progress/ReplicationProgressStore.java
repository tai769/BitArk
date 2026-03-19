package com.bitark.engine.replication.progress;

import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


@Slf4j
public class ReplicationProgressStore {

    private static final int VERSION = 1;
    private final Path path;
    public ReplicationProgressStore(Path path) {
        this.path = path;
    }



    public void save(Long globalLsn)throws IOException{
        if (path.getParent() != null){
            Files.createDirectories(path.getParent());
        }
        try(DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))){
            out.writeInt(VERSION);
            out.writeLong(globalLsn);
        }
    }

    public Long load() throws IOException{
        if (!Files.exists(path)){
            log.error("No replication progress file found at {}", path);
            return null;
        }
        try(DataInputStream in = new DataInputStream(Files.newInputStream(path))){
            int version = in.readInt();
            Long lsn = in.readLong();
           return lsn;
        }
    }
}
