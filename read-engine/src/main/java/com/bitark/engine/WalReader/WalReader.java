package com.bitark.engine.WalReader;

import com.bitark.commons.log.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class WalReader implements AutoCloseable{

    public long replay(String path, WalRecordHandler handler) throws  Exception{
        try(RandomAccessFile raf = new RandomAccessFile(path,"rw");
            FileChannel channel = raf.getChannel();
        ){
            while (true){
                long recordOffset = channel.position();

                WalRecord record = readOneRecord(channel);

                if (record == null){
                    return recordOffset;
                }
                handler.handle(record);
            }
        }

    }



    private WalRecord readOneRecord(FileChannel channel)throws IOException{
        long recordStartOffset = channel.position();

        ByteBuffer lengthBuffer = ByteBuffer.allocate(WalRecordCodec.RECORD_LENGTH_SIZE);
        int lengthBytes = readFull(channel, lengthBuffer);
        if (lengthBytes == -1){
            return null;
        }
        if (lengthBytes < WalRecordCodec.RECORD_LENGTH_SIZE ){
            channel.truncate(recordStartOffset);
            throw new IOException("Bad log entry at offset " + recordStartOffset);
        }

        lengthBuffer.flip();
        int recordLength = lengthBuffer.getInt();
        if (recordLength < WalRecordCodec.FIXED_HEADER_SIZE + WalRecordCodec.CRC_SIZE ){
            channel.truncate(recordStartOffset);
            throw new IOException("Invalid record length at offset " + recordStartOffset + ": " + recordLength);
        }
        ByteBuffer recordBuffer = ByteBuffer.allocate(recordLength);
        recordBuffer.putInt(recordLength);

        int bodyBytes = readFull(channel, recordBuffer);
        int expectedBodyBytes = recordLength - WalRecordCodec.RECORD_LENGTH_SIZE;
        if (bodyBytes < expectedBodyBytes) {
            channel.truncate(recordStartOffset);
            throw new IOException("Incomplete record body at offset " + recordStartOffset);
        }
        recordBuffer.flip();
        try {
            return WalRecordCodec.decode(recordBuffer);
        } catch (RuntimeException e) {
            channel.truncate(recordStartOffset);
            throw new IOException("Bad wal record at offset " + recordStartOffset, e);
        }
    }


    public long replay(String path, long startOffset, WalRecordHandler handler) throws Exception {
        try(RandomAccessFile raf = new RandomAccessFile(path,"rw");
            FileChannel channel = raf.getChannel();
        ){
            //从指定的 offset
            channel.position(startOffset);


            while (true) {
                long recordOffset = channel.position();
                WalRecord record = readOneRecord(channel);
                if (record == null){
                    return recordOffset;
                }
                handler.handle(record);
            }
        }
    }

    private int readFull(FileChannel channel,
                         ByteBuffer buffer) throws IOException {
        int totalRead = 0;
        while (buffer.hasRemaining()){
            int n = channel.read(buffer);
            if (n == -1){
                return (totalRead == 0) ? -1 : totalRead;
            }
            totalRead += n;
        }
        return totalRead;
    }


    public FileReadBatch readBatch(String path, long startOffset, int maxBytes)throws Exception{
        File file = new File(path);
        if(!file.exists()){
            throw  new FileNotFoundException("Wal Segment not exist " + path);
        }
        List<WalRecord> records = new ArrayList<>();
        int totalBytes = 0;
        try(RandomAccessFile raf = new RandomAccessFile(path,"r");
            FileChannel channel = raf.getChannel()){
            channel.position(startOffset);
            while (totalBytes < maxBytes){

                long recordOffset = channel.position();
                WalRecord record = readOneRecord(channel);
                if (record == null){
                    return new FileReadBatch(records, recordOffset, true);
                }
                int recordLength = WalRecordCodec.recordLength(record);
                if (totalBytes + recordLength  > maxBytes){
                    if (records.isEmpty()){
                        records.add( record);
                        return new FileReadBatch(records, recordOffset, false);
                    }
                    channel.position(recordOffset);
                    return new FileReadBatch(records, channel.position(), false);
                }
                records.add(record);
                totalBytes += recordLength;
            }
            return new FileReadBatch(records, channel.position(), false);
        }

    }

    @Override
    public void close() throws Exception {
        // WalReader_V1 使用 try-with-resources 自动管理资源
        // 所以不需要额外的清理操作
    }
}
