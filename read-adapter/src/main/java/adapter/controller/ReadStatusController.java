package adapter.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import service.ReadServiceImpl;

@RestController
@RequestMapping("/api")
public class ReadStatusController {

    @Resource
    private ReadServiceImpl readService;

    // 标记消息为已读
    @PostMapping("/mark")
    public String markAsRead(@RequestParam Long userId, @RequestParam Long msgId) {
        try {
            readService.read(userId, msgId);
            return "Message marked as read successfully for user: " + userId + ", msg: " + msgId;
        } catch (Exception e) {
            return "Error marking message as read: " + e.getMessage();
        }
    }

    // 检查消息是否已读
    @GetMapping("/check")
    public boolean isRead(@RequestParam Long userId, @RequestParam Long msgId) {
        return readService.isRead(userId, msgId);
    }

    // 恢复数据
    @PostMapping("/recover")
    public String recover() {
        try {
            readService.recover();
            return "Recovery completed successfully";
        } catch (Exception e) {
            return "Error during recovery: " + e.getMessage();
        }
    }
}