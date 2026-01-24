package com.bitark.engine.recover;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.bitark.engine.ReadStatusEngine;

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

}
