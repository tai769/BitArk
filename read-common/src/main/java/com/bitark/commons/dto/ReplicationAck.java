package com.bitark.commons.dto;



import com.bitark.commons.wal.WalCheckpoint;
import lombok.Data;

@Data
public class ReplicationAck {

    private int ackSegmentIndex;
    private long ackOffset;
    
    // 快捷方法：把回执转回 Checkpoint 对象方便 Master 比较
    public WalCheckpoint toCheckpoint() {
        return new WalCheckpoint(1, ackSegmentIndex, ackOffset); 
    }

}
