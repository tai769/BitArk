package service;

import java.io.IOException;


import lombok.extern.slf4j.Slf4j;
import wal.LogEntry;
import wal.WalWriter_V2;

@Slf4j
public class ReadServiceImpl  implements ReadService{

   


    //只负责 wal的操作
    @Override
    public void read(Long userId, Long msgId) throws IOException {
        WalWriter_V2 walWriter = WalWriter_V2.getInstance();
        LogEntry entry = new LogEntry(LogEntry.READ_ENTRY,userId, msgId);
        walWriter.append(entry);
    }

    



}
