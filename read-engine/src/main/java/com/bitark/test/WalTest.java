package com.bitark.test;

import java.util.concurrent.CompletableFuture;

import com.bitark.log.LogEntry;
import com.bitark.log.LogEntryHandler;
import com.bitark.wal.WalReader.WalReader_V1;
import com.bitark.wal.WalWriter.WalWriter_V2;


public class WalTest {

/*
    public static void main(String[] args) {
        // 测试wal reader 和wal writer
        WalWriter_V2 walWriter;
        try {
            walWriter = new WalWriter_V2("wal.log", 0);
            CompletableFuture<Boolean> future1 = walWriter.append(new LogEntry((byte) 0, 22L, 2L));
            future1.get(); // 等待写入完成
            walWriter.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        long replay = 0;

        WalReader_V1 walReader;
        try{
            walReader = new WalReader_V1();
            replay = walReader.replay("wal.log", new LogEntryHandler() {
                            @Override
                            public void handle(LogEntry entry) {
                                System.out.println(entry);
                            }
                        });;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        WalWriter_V2 walWriter_V2;
        try{
            walWriter_V2 = new WalWriter_V2("wal.log", replay);
            CompletableFuture<Boolean> future2 = walWriter_V2.append(new LogEntry((byte) 0, 1L, 2L));
            future2.get(); // 等待写入完成
            walWriter_V2.close();
        }catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }



        try{
            walReader = new WalReader_V1();
            replay = walReader.replay("wal.log", new LogEntryHandler() {
                @Override
                public void handle(LogEntry entry) {
                    System.out.println(entry);
                }
            });;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
*/

}
