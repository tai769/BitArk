package com.bitark.engine.userset;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public interface UserReadSet {

    void mark(long msgId);

    boolean isRead(long msgId);

    void toSnapshot(DataOutputStream out);

    void loadSnapshot(DataInputStream in);

}
