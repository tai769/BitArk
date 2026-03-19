package com.bitark.commons.wal;

import lombok.Data;

@Data
public class WalCheckpoint {
    int segmentIndex;
    long segmentOffset; 

    public WalCheckpoint(int segmentIndex, long segmentOffset) {
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
