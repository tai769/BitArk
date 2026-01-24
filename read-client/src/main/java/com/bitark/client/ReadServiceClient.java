package com.bitark.client;

import com.bitark.cluster.NodeInfo;
import com.bitark.cluster.ShardingStrategy;
import org.springframework.web.client.RestTemplate;


import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReadServiceClient {

    @Resource
    private ShardingStrategy shardingStrategy;

    @Resource
    private RestTemplate restTemplate;

    public ReadServiceClient(ShardingStrategy shardingStrategy, RestTemplate restTemplate) {
        this.shardingStrategy = shardingStrategy;
        this.restTemplate = restTemplate;
    }

    public void markAsRead(Long userId, Long msgId) {
        // 1. 寻找路由
        NodeInfo nodeInfo = shardingStrategy.route(userId);

        // 2. 拼接地址
        String url = String.format("http://%s:%d/api/mark?userId=%d&msgId=%d",
                nodeInfo.getNodeAddress(), nodeInfo.getNodePort(), userId, msgId);

        // 3. 发送请求
        restTemplate.postForObject(url, null, String.class);
    }

    public boolean isRead(Long userId, Long msgId) {
        // 1. 寻找路由
        NodeInfo nodeInfo = shardingStrategy.route(userId);

        // 2. 拼接地址
        String url = String.format("http://%s:%d/api/check?userId=%d&msgId=%d",
                nodeInfo.getNodeAddress(), nodeInfo.getNodePort(), userId, msgId);

        // 3. 发送请求
        Boolean result = restTemplate.getForObject(url, Boolean.class);
        if (result != null) {
            return result;
        }
        log.info("isRead failed");
        return false;
    }

}
