package com.bitark.wal.WalReader;

import com.bitark.engine.WalEngine;
import lombok.extern.slf4j.Slf4j;
import com.bitark.log.LogEntry;
import com.bitark.log.LogEntryHandler;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

@Slf4j
public class WalReader_V1 implements AutoCloseable{

    public long replay(String path, LogEntryHandler handler) throws  Exception{
        try(RandomAccessFile raf = new RandomAccessFile(path,"rw");
            FileChannel channel = raf.getChannel();
        ){
            ByteBuffer buffer = ByteBuffer.allocate(LogEntry.ENTRY_SIZE);

            while ( true){
                long startOffset = channel.position();

                //1. 尝试填满21个字节
                buffer.clear();
                int bytesRead = readFull(channel, buffer);
                if (bytesRead == -1){
                    return startOffset; //文件完美读完 ;
                }
                if (bytesRead < LogEntry.ENTRY_SIZE){
                    // 如果读取到的字段 < 21 说明最后一条日志是坏的
                    log.error("Bad log entry at offset {}", startOffset);
                    channel.truncate(startOffset); // 截断文件
                    return startOffset;
                }
                buffer.flip();
                try {
                    LogEntry entry = LogEntry.decode(buffer);
                    handler.handle(entry);//回调给业务
                } catch (RuntimeException e) {
                    log.error("日志条目解析失败: {}", e.getMessage());
                    // 截断到当前条目的起始位置，丢弃损坏的数据
                    channel.truncate(startOffset);
                    return startOffset;
                }
            }
        }

    }

    private int readFull(FileChannel channel,
                         ByteBuffer buffer) throws IOException {
        int totalRead = 0;
        while (buffer.hasRemaining()){
            int n = channel.read(buffer);
            if (n == -1){
                return (totalRead == 0) ? 0 : totalRead;
            }
            totalRead += n;
        }
        return totalRead;
    }

    @Override
    public void close() throws Exception {
        // WalReader_V1 使用 try-with-resources 自动管理资源
        // 所以不需要额外的清理操作
    }
}
