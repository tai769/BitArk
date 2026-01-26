package com.bitark.adapter.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.engine.service.ReadService;

/*
* 内部同步接口
* 不对外公网开放,仅用于集群内部 Master -> Slave的数据复制
*/
@RestController
@RequestMapping("/internal")
public class InternalSyncController {

    @Resource
    private ReadService readService;

    @PostMapping("/sync")
    public String sync(@RequestBody ReplicationRequest req)throws Exception {
        try {
            // 1. 调用本地业务逻辑（它内部会写 WAL 并产生本地进度）
            // 注意：这里我们目前直接透传 Master 的 LSN 作为 ACK 即可，
            // 表示“你给我的这个 LSN 我已经搞定了”。
            readService.readFromMaster(req.getUserId(), req.getMsgId());

            // 2. 返回回执
            ReplicationAck ack = new ReplicationAck();
            ack.setAckSegmentIndex(req.getSegmentIndex());
            ack.setAckOffset(req.getOffset());
            return "ack";
        } catch (Exception e) {
            throw new RuntimeException("sync failed", e);
        }
    }
}
