package com.bitark.engine.checkpoint;

import lombok.Data;

@Data
public class WalCheckpoint {
    int version;
    int segmentIndex;
    long segmentOffset; 

    public WalCheckpoint(int version, int segmentIndex, long segmentOffset) {
        this.version = version;
        this.segmentIndex = segmentIndex;
        this.segmentOffset = segmentOffset;
    }
}
