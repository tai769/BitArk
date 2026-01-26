package com.bitark.commons.wal;

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

    public int compareTo(WalCheckpoint other){
        int cmp = Long.compare(this.segmentIndex,other.segmentIndex);
        if (cmp != 0) {
            return cmp;
        }
        return Long.compare(this.segmentOffset,other.segmentOffset);
    }
}
