package test;




import com.bitark.client.ReadServiceClient;
import com.bitark.cluster.NodeInfo;
import com.bitark.cluster.ShardingStrategy;
import org.springframework.web.client.RestTemplate;

import java.util.List;

public class shareIdTest {
    

    public static void main(String[] args) {
        NodeInfo nodeInfo = new NodeInfo(1, "node1", "127.0.0.1", 8080);

        NodeInfo nodeInfo2 = new NodeInfo(2, "node2", "127.0.0.1", 8081);

        NodeInfo nodeInfo3 = new NodeInfo(3, "node3", "127.0.0.1", 8082);

        List<NodeInfo> nodeList = List.of(nodeInfo, nodeInfo2, nodeInfo3);

        ShardingStrategy strategy = new ShardingStrategy(3, nodeList);

        //使用 restTemplate
        RestTemplate realRestTemplate = new RestTemplate();
        ReadServiceClient client = new ReadServiceClient(strategy, realRestTemplate);
        boolean result1 = client.isRead(1L, 4L);
        System.out.println("用户1的消息4已读状态: " + result1);
        // 步骤1: 标记用户1的消息1为已读状态
        client.markAsRead(1L, 4L);
        // 步骤2: 检查用户1的消息1是否为已读状态，预期返回true
        result1 = client.isRead(1L, 4L);
        System.out.println("用户1的消息4已读状态: " + result1);


        boolean result2 = client.isRead(2L, 1L);
        System.out.println("用户2的消息5已读状态: " + result2);
        // 步骤3: 标记用户2的消息2为已读状态
        client.markAsRead(2L, 1L);
        // 步骤4: 检查用户2的消息2是否为已读状态，预期返回true
        result2 = client.isRead(2L, 1L);
        System.out.println("用户2的消息5已读状态: " + result2);


        boolean result3 = client.isRead(3L, 6L);
        System.out.println("用户3的消息6已读状态: " + result3);
        // 步骤5: 标记用户3的消息3为已读状态
        client.markAsRead(3L, 6L);
        // 步骤6: 检查用户3的消息3是否为已读状态，预期返回true
        result3 = client.isRead(3L, 6L);
        System.out.println("用户3的消息6已读状态: " + result3);
        
        
        // 测试路由一致性
        Long userId = 12345L;

        // 调用10次，验证路由结果是否一致
        NodeInfo firstNode = strategy.route(userId);
        for (int i = 0; i < 10; i++) {
            NodeInfo node = strategy.route(userId);
            assert node.getNodeId() == firstNode.getNodeId();
        }
        System.out.println("路由一致性测试通过");

    }
    


}
