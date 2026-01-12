package com.bitark.engine.userset;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SetBasedUserReadSet implements UserReadSet {

       private final Set<Long> set = ConcurrentHashMap.newKeySet();

        @Override
        public void mark(long msgId) {
            set.add(msgId);
        }

        @Override
        public boolean isRead(long msgId) {
            return set.contains(msgId);
        }

        @Override
        public void toSnapshot(DataOutputStream out) {
            try{
                out.writeInt(set.size());
                for (long msgId : set) {
                    out.writeLong(msgId);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        @Override
        public void loadSnapshot(DataInputStream in) {
            try{
                set.clear();
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    set.add(in.readLong());
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

}
