package com.bitark.commons.dto;



import com.bitark.commons.lsn.LsnPosition;
import lombok.Data;

@Data
public class ReplicationAck {

    private String slaveUrl; // slave的唯一标识
    private int ackSegmentIndex;
    private long ackOffset;

    public LsnPosition toLsnPosition() {
        return new LsnPosition(ackSegmentIndex, ackOffset); 
    }

}
