package com.bitark.engine.service.apply;

import com.bitark.commons.command.ReadMarkCommand;
import com.bitark.commons.command.ReadMarkCommandCodec;
import com.bitark.commons.enums.CommandTypes;
import com.bitark.commons.log.WalRecord;
import com.bitark.engine.ReadStatusEngine;
import lombok.Data;

@Data
public class ReadStateMachineApplier {
    private final ReadStatusEngine engine;

    public  void apply(WalRecord record){
        if (record.getType() == CommandTypes.READ_MARK){
            ReadMarkCommand command =
                    ReadMarkCommandCodec.decode(record.getPayload());

            engine.markRead(command.getUserId(),
                    command.getMsgId(),
                    record.getLeaderLsn());
            return;
        }
        throw  new IllegalArgumentException("unknow command type");
    }
}
