package com.bitark.adapter.controller;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import com.bitark.engine.service.ReadServiceImpl;

@RestController
@RequestMapping("/read")
public class ReadStatusController {

    @Resource
    private ReadServiceImpl readService;

    // 标记消息为已读
    @PostMapping("/mark")
    public String markAsRead(@RequestParam("userId") Long userId, @RequestParam("msgId") Long msgId) {
        try {
            readService.read(userId, msgId);
            return "Message marked as read successfully for user: " + userId + ", msg: " + msgId;
        } catch (Exception e) {
            return "Error marking message as read: " + e.getMessage();
        }
    }

    // 检查消息是否已读
    @GetMapping("/check")
    public boolean isRead(@RequestParam("userId") Long userId, @RequestParam("msgId") Long msgId) {
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

    @PostMapping("/snapshot")
    public String snapshot() {
        try {
            readService.snapshot();
            return "Snapshot 已保存";
        } catch (Exception e) {
            return "Snapshot 失败: " + e.getMessage();
        }
    }
}