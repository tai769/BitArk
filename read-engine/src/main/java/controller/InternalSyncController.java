package service;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public String sync(@RequestParam("userId") Long userId, @RequestParam("msgId") Long msgId) {
        try {
            readService.read(userId, msgId);
            return "ack";
        } catch (Exception e) {
            throw new RuntimeException("sync failed", e);
        }
    }
}
