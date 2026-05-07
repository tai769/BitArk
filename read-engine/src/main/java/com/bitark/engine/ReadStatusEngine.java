package com.bitark.engine;




import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.bitark.engine.userset.RoaringUserReadSet;
import com.bitark.engine.userset.SetBasedUserReadSet;
import com.bitark.engine.userset.UserReadSet;
import com.bitark.commons.enums.UserReadSetMode;

import lombok.extern.slf4j.Slf4j;




@Slf4j
public class ReadStatusEngine {




    private volatile  ConcurrentHashMap<Long, UserReadSet> activeStatus;

    private volatile  ConcurrentHashMap<Long, UserReadSet> frozenStatus;

    private boolean snapshotInProgress = false;

    private final ReentrantLock versionLock = new ReentrantLock();

    private long lastAppliedLsn;

    private final UserReadSetMode mode;
   

    public ReadStatusEngine() {
        this.mode = UserReadSetMode.ROARING;
        this.activeStatus = new ConcurrentHashMap<>();
        this.frozenStatus = null;
        this.lastAppliedLsn = 0L;
    }
    
    public ReadStatusEngine(UserReadSetMode mode) {
        this.mode = mode;
        this.activeStatus = new ConcurrentHashMap<>();
    }

 


    public void markRead(Long userId, Long msgId, Long lsn) {
        versionLock.lock();
        try{
            activeStatus.computeIfAbsent(userId, k -> newUserReadSet()).mark(msgId);
            lastAppliedLsn = Math.max(lsn, lastAppliedLsn);
        }finally {
            versionLock.unlock();
        }
    }




    public boolean isRead(Long userId, Long msgId) {
        UserReadSet active = activeStatus.get(userId);
        if (active != null && active.isRead(msgId)){
            return true;
        }
        ConcurrentHashMap<Long, UserReadSet> frozen = frozenStatus;
        if (frozen == null) {
            return false;
        }

        UserReadSet frozenSet = frozen.get(userId);
        return frozenSet != null && frozenSet.isRead(msgId);
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
        
    

    public void saveSnapshot(DataOutputStream out){
        try{
            out.writeInt(activeStatus.size());
            for(Map.Entry<Long, UserReadSet> entry : activeStatus.entrySet()){
                out.writeLong(entry.getKey());
                entry.getValue().toSnapshot(out);
            }
            
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void loadSnapshot(DataInputStream in){
        try{
            activeStatus.clear();
            int size = in.readInt();
            for(int i = 0; i < size; i++){
                Long userId = in.readLong();
                UserReadSet userReadSet = newUserReadSet();
                userReadSet.loadSnapshot(in);
                activeStatus.put(userId, userReadSet);
                frozenStatus = null;
                snapshotInProgress = false;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }






 


  
}
