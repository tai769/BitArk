# BitArk 设计日志：主从对账机制与安全 GC 策略

**日期：** 2026-01-27  
**设计者：** BitArk 架构团队  
**状态：** Phase 1.2 完成 - 基础主从复制与进度追踪

---

## 1. 今晚的核心问题：LSN 有了，然后呢？

昨天（2026-01-26）我们完成了 LSN 的物理坐标映射，Master 能够在写 WAL 时产生精确的 `(segmentIndex, offset)`。

**但今晚我们发现了一个严重的架构隐患**：
*   Master 生成了 LSN，却没人用它。
*   Master 不知道 Slave 追到哪了。
*   Master 在做快照清理（GC）时，只看自己的进度，可能把 Slave 还需要的日志删掉。

**后果**：Slave 断线重连后，发现自己需要的日志已经被 Master 删除，数据永久断层，系统崩溃。

---

## 2. 第一性原理：分布式系统的"进度对齐"

### 核心矛盾
在主从架构中，存在三个独立的时间轴：
1.  **Master 的写入进度**（LSN_master）：我写到哪了。
2.  **Slave 的接收进度**（LSN_slave_received）：我收到哪了（可能还在网络上传输）。
3.  **Slave 的确认进度**（LSN_slave_ack）：我已经落盘到哪了。

**只有 LSN_slave_ack 是可靠的。**

### 工业界的解决方案
*   **Kafka**：ISR（In-Sync Replica）机制 - 只有所有 ISR 成员都确认的消息，才会被标记为"可提交"。
*   **MySQL**：半同步复制 - Master 写 Binlog 后，至少要等一个 Slave 确认落盘，才返回给客户端。
*   **RocketMQ**：HA 组件 - Master 维护 Slave 的同步进度（replicateOffset），GC 时以最慢的 Slave 为准。

**我们的选择**：参考 RocketMQ，Master 维护一个"Slave 进度账本"，GC 时以最慢的 Slave 为准。

---

## 3. 设计思路演进（今晚的思考链路）

### 阶段 1：定义"主从共同语言"（协议设计）

**问题**：Master 怎么把 LSN 传给 Slave？Slave 怎么回传自己的进度？

**方案**：设计两个 DTO（数据传输对象）
*   `ReplicationRequest`：Master 发给 Slave 的包裹，内含 `userId`, `msgId`, `segmentIndex`, `offset`。
*   `ReplicationAck`：Slave 返回给 Master 的回执，内含 `ackSegmentIndex`, `ackOffset`。

**为什么用 JSON 而不是二进制协议？**
*   渐进式开发：先用 HTTP + JSON 跑通逻辑，后续如果性能瓶颈，再升级为 Netty + Protobuf。
*   可调试性：JSON 可读，便于用 curl 测试。

**关键决策**：把 `WalCheckpoint` 从 `read-engine` 下沉到 `read-common`，让它成为全局共识的"度量单位"。

---

### 阶段 2：改造 Master 的"发货"逻辑

**问题**：Master 异步通知 Slave 时，只发了 `userId` 和 `msgId`，没有发 LSN。

**方案**：
1.  在 `ReadServiceImpl.read()` 方法里，接住 `walEngine.append(entry)` 的返回值（LSN）。
2.  在异步线程里，构造 `ReplicationRequest` 并填入 LSN。
3.  用 `RestTemplate.postForObject()` 发送 JSON，并接收 `ReplicationAck`。
4.  将 Slave 的回执登记到 `slaveAckMap`（内存账本）。

**为什么用 ConcurrentHashMap？**
*   Master 可能同时收到多个 Slave 的回执（并发场景）。
*   `ConcurrentHashMap` 是线程安全的哈希表，无需手动加锁。

---

### 阶段 3：改造 Slave 的"签收"逻辑

**问题**：Slave 的 `InternalSyncController` 接收请求后，返回的是字符串 `"ack"`，而不是 JSON 对象。

**方案**：
1.  将方法返回类型从 `String` 改为 `ReplicationAck`。
2.  构造回执对象，填入 Master 传来的 LSN（表示"我已经处理到这个位置了"）。
3.  直接 `return ack`，Spring 会自动序列化成 JSON。

**为什么要返回 LSN？**
*   虽然目前 Slave 只是简单透传 Master 的 LSN，但将来当 Slave 有自己的本地 Checkpoint 时，它可以返回自己真实的进度。

---

### 阶段 4：重新审视 Master 的 GC 逻辑

**问题**：`snapshot()` 方法在清理 WAL 时，用的是 Master 自己的 `currCheckpoint`，没有考虑 Slave 的进度。

**方案**：
1.  新增 `getMinSlaveAckLSN()` 方法：遍历 `slaveAckMap`，找出进度最慢的 Slave。
2.  改造 `snapshot()` 的 GC 逻辑：
    *   计算 `safeCheckpoint = min(masterCheckpoint, minSlaveCheckpoint)`。
    *   只删除严格小于 `safeCheckpoint.segmentIndex` 的文件。

**为什么要取最小值？**
*   类比班级交作业：只有当**最慢的同学**都交到第 5 页了，老师才能回收前 4 页的草稿纸。
*   如果有 Slave 还停留在 Segment 2，就不能删除 Segment 2 及之后的文件。

**边界情况处理**：
*   如果 `slaveAckMap` 为空（没有 Slave），退化为单机模式，用 Master 自己的进度。
*   如果 Slave 的进度反而比 Master 新（理论上不可能，但容错处理），取 Master 的进度。

---

## 4. 架构价值与未来扩展

### 本次改动的核心价值
1.  **可追溯性**：Master 知道每个 Slave 的实时进度，可以精准判断"谁慢了"。
2.  **安全 GC**：不再因为 Master 的"自私"删除，导致 Slave 数据断层。
3.  **为断线重连打基础**：将来 Slave 重启后，可以通过 `/internal/register` 接口告诉 Master："我上次追到 LSN=100，请从那里给我补发"。

### 未解决的问题（后续演进方向）
1.  **Slave 挂了怎么办？**
    *   目前 `slaveAckMap` 里的进度会一直保留，即使 Slave 已经永久下线。
    *   解决方案：心跳检测 + 超时剔除（Phase 2.0）。
2.  **网络抖动导致 ACK 丢失？**
    *   目前如果 Slave 的回执因为网络问题没有到达 Master，Master 会误以为 Slave 还停留在旧进度。
    *   解决方案：Slave 定期主动上报进度（Phase 2.5）。
3.  **Slave 追不上怎么办？**
    *   如果某个 Slave 长期落后，会拖累整个集群的 GC 速度（磁盘爆满）。
    *   解决方案：慢从机降级 + 告警（Phase 3.0）。

---

## 5. 今晚的技术决策记录

| 决策点 | 选项 A | 选项 B（我们的选择） | 理由 |
|:---|:---|:---|:---|
| 协议格式 | 二进制（Protobuf） | JSON | 渐进式开发，先跑通逻辑 |
| Slave 进度存储 | 持久化到磁盘 | 内存 Map（ConcurrentHashMap） | 简化实现，后续可升级 |
| GC 策略 | 固定时间清理 | 按最慢 Slave 清理 | 保证数据不丢 |
| DTO 位置 | 各自定义 | 下沉到 common | 避免循环依赖 |

---

## 6. 对比工业界方案

### RocketMQ 的 HA 复制
*   **HAConnection**：Master 为每个 Slave 维护一个长连接。
*   **replicateOffset**：每个连接记录 Slave 的同步进度。
*   **GC 策略**：`min(checkpoint, min(slaveOffset))`，与我们的思路一致。

### Kafka 的 ISR 机制
*   **高水位线（HWM）**：只有所有 ISR 成员都确认的消息，才对消费者可见。
*   **Follower 主动拉取**：与我们的 Master 推送不同，Kafka 是 Follower 定期发 Fetch 请求。
*   **我们的差异**：目前是 Master 推，将来可以改成 Slave 拉（更灵活）。

---

## 7. 下一步行动

### 已完成
- [x] LSN 物理坐标映射（昨天）
- [x] 主从对账协议设计（今晚）
- [x] Master 登记 Slave 进度（今晚）
- [x] 安全 GC 策略（今晚）

### 待完成（按优先级）
- [ ] **Phase 2.0**：心跳检测 + Slave 自动剔除
- [ ] **Phase 2.5**：Slave 定期主动上报进度
- [ ] **Phase 3.0**：慢从机降级与告警
- [ ] **Phase 4.0**：断线重连与增量补发

---

*BitArk - 一步一个脚印，构建工业级分布式存储*
