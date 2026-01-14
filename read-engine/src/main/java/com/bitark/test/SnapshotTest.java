package com.bitark.test;

import java.io.IOException;
import java.nio.file.Paths;

import com.bitark.engine.ReadStatusEngine;
import com.bitark.enums.UserReadSetMode;
import com.bitark.util.SnapshotManager;

public class SnapshotTest {

    public static void main(String[] args) throws IOException {
        ReadStatusEngine engine = new ReadStatusEngine(UserReadSetMode.ROARING);
        engine.markRead(1L, 100L);
        engine.markRead(1L, 101L);
        engine.markRead(2L, 200L);
        engine.markRead(3L, 300L);
        engine.markRead(3L, 301L);

        SnapshotManager manager = new SnapshotManager(Paths.get("/home/qiushui/IdeaProjects/BitArk/snapshot-test.bin"));
        manager.save(engine);
        System.out.println("✅ Snapshot 已保存");

         // 3. 创建新引擎，从 snapshot 恢复
        ReadStatusEngine engine2 = new ReadStatusEngine(UserReadSetMode.ROARING);
        manager.load(engine2);
        System.out.println("✅ Snapshot 已恢复");

        // 4. 验证数据一致
        assert engine2.isRead(1L, 100L) : "user=1, msg=100 应该已读";
        assert engine2.isRead(1L, 101L) : "user=1, msg=101 应该已读";
        assert !engine2.isRead(1L, 102L) : "user=1, msg=102 应该未读";
        assert engine2.isRead(2L, 200L) : "user=2, msg=200 应该已读";
        assert engine2.isRead(3L, 300L) : "user=3, msg=300 应该已读";
        assert engine2.isRead(3L, 301L) : "user=3, msg=301 应该已读";

        System.out.println("✅ 所有验证通过！Snapshot 功能正常");
    }

}
