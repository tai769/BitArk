package engine;



import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;




@Slf4j
public class ReadStatusEngine {


    //这里先用concurrent
    ConcurrentHashMap<Long, Set<Long>> readStatus = new ConcurrentHashMap<>();




    public ReadStatusEngine( ConcurrentHashMap<Long, Set<Long>> readStatus) {
        this.readStatus = readStatus;
    }

 

    public boolean isRead(Long userId, Long msgId) {
      
        return readStatus.get(userId).contains(msgId);
    }





 


  
}
