package com.bitark.engine.userset;

public interface UserReadSet {

    void mark(long msgId);

    boolean isRead(long msgId);

}
