package com.bitark.commons.lsn;

import lombok.Data;

@Data
public class LsnPosition {

    private int segmentIndex;

    private long offset;

    public LsnPosition(int segmentIndex, long offset) {
        this.segmentIndex = segmentIndex;
        this.offset = offset;
    }

    public int compareTo(LsnPosition other){
        int cmp = Integer.compare(this.segmentIndex, other.segmentIndex);
        if (cmp != 0) {
            return cmp;
        }
        return Long.compare(this.offset, other.offset);
    }

}
