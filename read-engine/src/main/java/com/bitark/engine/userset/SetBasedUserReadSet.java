package com.bitark.engine.userset;

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

}
