package engine;




import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;




@Slf4j
public class ReadStatusEngine {

    private ConcurrentHashMap<Long, Set<Long>> readStatus;
   

    public ReadStatusEngine() {
        this.readStatus = new ConcurrentHashMap<>();
        ;
    }

 


    public void markRead(Long userId, Long msgId) {
        readStatus.computeIfAbsent(userId, k ->  ConcurrentHashMap.newKeySet()).add(msgId);  
    }




    public boolean isRead(Long userId, Long msgId) {
        return readStatus.getOrDefault(userId, ConcurrentHashMap.newKeySet()).contains(msgId);
    }
        
         





 


  
}
