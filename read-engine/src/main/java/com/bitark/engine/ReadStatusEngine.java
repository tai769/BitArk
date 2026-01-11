package com.bitark.engine;




import java.util.concurrent.ConcurrentHashMap;

import com.bitark.engine.userset.RoaringUserReadSet;
import com.bitark.engine.userset.SetBasedUserReadSet;
import com.bitark.engine.userset.UserReadSet;
import com.bitark.enums.UserReadSetMode;

import lombok.extern.slf4j.Slf4j;




@Slf4j
public class ReadStatusEngine {

    private ConcurrentHashMap<Long, UserReadSet> readStatus;

     private final UserReadSetMode mode;
   

    public ReadStatusEngine() {
        this.mode = UserReadSetMode.ROARING;
        this.readStatus = new ConcurrentHashMap<>();
        ;
    }
    
    public ReadStatusEngine(UserReadSetMode mode) {
        this.mode = mode;
        this.readStatus = new ConcurrentHashMap<>();
    }

 


    public void markRead(Long userId, Long msgId) {
        readStatus.computeIfAbsent(userId, k -> newUserReadSet()).mark(msgId);  
    }




    public boolean isRead(Long userId, Long msgId) {
        UserReadSet userReadSet = readStatus.get(userId);
        return userReadSet != null && userReadSet.isRead(msgId);
    }


    private UserReadSet newUserReadSet() {
        switch (mode) {
            case SET:
                return new SetBasedUserReadSet();
            case ROARING:
                return new RoaringUserReadSet();
            default:
                throw new IllegalArgumentException("Unknown UserReadSetMode: " + mode);
        }

    }
        
         





 


  
}
