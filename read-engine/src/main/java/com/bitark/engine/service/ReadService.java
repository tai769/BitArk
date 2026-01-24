package com.bitark.engine.service;


public interface ReadService {
    void read(Long userId, Long msgId) throws Exception;


    void recover() throws Exception;

    boolean isRead(Long userId, Long msgId);

    void readFromMaster (Long userId, Long msgId)throws Exception;


    void snapshot() throws Exception;


}
