/*
* 实现分片策略
*/

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ShardingStrategy {

    private final int shardCount;

    //这里 map 存储的是 sharId和 Node信息
    private final Map<Integer, NodeInfo> shardMap;


    public ShardingStrategy(int shardCount, List<NodeInfo> nodeList) {
        this.shardCount = shardCount;
        this.shardMap = new HashMap<Integer, NodeInfo>();
        
        for(int i = 0; i < shardCount; i++){
            int nodeIndex = i % nodeList.size();
            shardMap.put(i, nodeList.get(nodeIndex));
        }
    }


    /**
     * 根据分片键(userId)获取分片ID
     * @param userId 
     * @return
     */
    private int getShareIdIndex(Long userId){
        int hashCode = userId.hashCode();
        return Math.abs(hashCode % 0x7fffffff);
    } 

    public NodeInfo route(Long userId){
        int shareIdIndex = getShareIdIndex(userId);
        return shardMap.get(shareIdIndex);
    }
}
