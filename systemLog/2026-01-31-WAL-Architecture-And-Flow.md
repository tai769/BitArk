# BitArk WAL 全链路架构与面试讲解稿（2026-01-31）

> 目标读者：面试官 / 评审
> 场景：高 QPS 已读服务内核（WAL + Snapshot + Replication）

---

## 1. 架构总览（模块分层）

**read-adapter**（入口层）
- Spring Boot 启动入口、Controller 路由
- 对外接口：`/read/*`
- 内部接口：`/internal/*`（复制/注册）

**read-engine**（核心业务层）
- ReadStatusEngine：内存位图存储（RoaringBitmap）
- WAL 引擎：`WalEngine` → `GroupCommitWalEngine` → `WalWriter_V2`/`WalReader_V1`
- 恢复体系：Snapshot + Checkpoint + 增量回放
- 复制体系：发送/接收/进度/报备
- 服务拆分：Facade + Command + Query

**read-common**（共享数据结构）
- `LogEntry`、`WalCheckpoint`、`LsnPosition`
- `ReplicationRequest/ReplicationAck`

**read-infrastructure**（基础设施）
- 线程池、RestTemplate、优雅关闭

**read-client / read-cluster**
- 客户端 SDK 与集群规划占位

---

## 2. 系统主链路（从上到下）

### 2.1 写入链路（用户标记已读）
```
/read/mark
  → ReadStatusController
    → ReadServiceImpl (Facade)
      → ReadCommandServiceImpl
        → WalEngine.append(LogEntry)
          → GroupCommitWalEngine
            → WalWriter_V2 (队列 + group commit + FileChannel)
        → ReadStatusEngine.markRead()   // 内存位图
        → ReplicationSender.sendRead()  // 触发复制
```
**关键点**
- WAL 先落盘，再更新内存（Crash Safe）
- Group Commit 把多条日志批量刷盘，提升吞吐
- 异步复制不影响主写延迟

---

### 2.2 查询链路（是否已读）
```
/read/check
  → ReadStatusController
    → ReadServiceImpl
      → ReadQueryServiceImpl
        → ReadStatusEngine.isRead()
```
**关键点**
- 纯内存查询，毫秒级
- 读路径与写路径隔离（CQRS 思路）

---

### 2.3 恢复链路（Crash 复活）
```
ReadServiceImpl.recover()
  → RecoveryCoordinatorImpl
    → SnapshotManager.load(snapshot.bin)
    → CheckpointManager.load(checkpoint.bin)
    → WalEngine.replayFrom(checkpoint)
```
**关键点**
- Snapshot 提供秒级恢复
- Checkpoint 指向对应 WAL 位置
- 只重放增量日志，避免全量回放

---

### 2.4 快照 & 安全 GC 链路
```
/read/snapshot
  → RecoveryCoordinator.snapshot()
    → SnapshotManager.save()
    → WalEngine.currCheckpoint() → CheckpointManager.save()
  → ReadServiceImpl.snapshot()
    → ReplicationTracker.getMinAckLsn()
    → WalEngine.gcOldSegment(safeCheckpoint)
```
**关键点**
- Snapshot + Checkpoint 固定一个安全恢复点
- GC 时用 **最慢 Slave 的 LSN** 防止误删

---

### 2.5 主从复制链路（Master 发送）
```
ReadCommandServiceImpl.read()
  → ReplicationSender.sendRead()
    → POST /internal/sync (Slave)
    → ReplicationAck
    → ReplicationTracker.registerAck(slaveId, lsn)
```
**关键点**
- 复制只发送新增日志
- Master 记录每个 Slave 的 ACK 进度

---

### 2.6 从库落盘链路（Slave 接收）
```
/internal/sync
  → SlaveReplicationServiceImpl.sync()
    → ReadCommandServiceImpl.applyReplication(req)
      → readFromMaster() // 写 WAL + 更新内存
      → ReplicationProgressStore.save(masterLsn)
    → 返回 ReplicationAck
```
**关键点**
- Slave 的持久化是独立 WAL
- LSN 代表 Master 的逻辑进度（用于追赶）

---

### 2.7 启动报备链路（Slave → Master）
```
ReadServiceImpl.recover()
  → ReplicationBootstrapper.reportIfPresent()
    → ReplicationProgressStore.load()
    → ReplicationReporter.reportStartup()
      → POST /internal/register
```
**关键点**
- Slave 恢复后主动报备
- Master 立即更新 ACK 账本，保证 GC 安全

---

## 3. 关键设计与职责拆分

### 3.1 语义边界
- **WalCheckpoint**：本地 WAL 坐标（version + segmentIndex + offset）
- **LsnPosition**：复制坐标（segmentIndex + offset）

**记忆口诀：**
- WalCheckpoint = 本地一致性
- LsnPosition = 复制一致性

### 3.2 服务拆分
- **ReadServiceImpl (Facade)**：对外统一入口
- **ReadCommandService**：写路径（read / readFromMaster / applyReplication）
- **ReadQueryService**：读路径（isRead）

**收益：**
- 写路径与读路径独立演进
- 复制逻辑只依赖 Command，不污染 Facade

### 3.3 复制组件拆分
- ReplicationSender：Master → Slave
- ReplicationTracker：维护各 Slave 的 ACK
- ReplicationProgressStore：Slave 持久化主 LSN
- ReplicationReporter：Slave 重启上报

---

## 4. 可靠性设计（面试重点）

### 4.1 WAL 可靠性
- 顺序写日志，避免随机写
- LogEntry 自带 CRC，检测静默损坏
- 恢复时发现坏日志直接 truncate，防止崩溃

### 4.2 Crash-Safe
- 每次写入 WAL 都可恢复
- Snapshot + Checkpoint + replayFrom 实现秒级启动

### 4.3 安全 GC
- 只删除 **小于最慢 Slave LSN** 的日志段
- 避免删掉 Slave 未追完的日志

### 4.4 复制可靠性
- Slave 进度持久化到 `replication-progress.bin`
- Slave 重启会自动报备，Master 更新账本
- 复制幂等：bitmap set 是天然幂等

---

## 5. 面试总结口径（建议背诵）

**一句话版：**
> BitArk 用 WAL + Snapshot 保证 crash-safe，用 replication 让多机副本一致，用 min-ack-LSN 做安全 GC，写路径批量落盘，读路径内存直查。

**三点重点（问到架构时）：**
1. 写路径：WAL 先落盘再更新内存，保证断电恢复
2. 恢复：Snapshot + Checkpoint + 增量回放，秒级启动
3. 复制：主从异步 + 进度持久化 + 启动报备，保证 GC 安全

---

## 6. 边界条件与后续增强（工业级补齐）

### 已处理
- WAL CRC 校验 + 崩溃截断
- 复制 ACK 账本
- Slave 重启上报

### 待补齐（下一阶段）
- **心跳**：解决 Master 长期不敢 GC 的问题
- **Full Sync**：当 Slave 落后太久日志被清掉时，走快照全量同步
- **重试 & 超时**：复制链路容错
- **Master/Slave 角色变更**：未来引入一致性协议（Raft/Paxos）

---

## 7. 文件索引（方便答辩时定位）

- WAL 引擎：`read-engine/src/main/java/com/bitark/engine/adapter/GroupCommitWalEngine.java`
- Writer：`read-engine/src/main/java/com/bitark/engine/WalWriter/WalWriter_V2.java`
- Reader：`read-engine/src/main/java/com/bitark/engine/WalReader/WalReader_V1.java`
- 内存引擎：`read-engine/src/main/java/com/bitark/engine/ReadStatusEngine.java`
- 恢复：`read-engine/src/main/java/com/bitark/engine/recover/RecoveryCoordinatorImpl.java`
- Command：`read-engine/src/main/java/com/bitark/engine/service/command/ReadCommandServiceImpl.java`
- Query：`read-engine/src/main/java/com/bitark/engine/service/query/ReadQueryServiceImpl.java`
- Replication Sender：`read-engine/src/main/java/com/bitark/engine/replication/sender/HttpReplicationSender.java`
- Replication Tracker：`read-engine/src/main/java/com/bitark/engine/replication/tracker/ReplicationTrackerImpl.java`
- Replication Bootstrap：`read-engine/src/main/java/com/bitark/engine/replication/bootstrap/ReplicationBootstrapper.java`
- Controller：`read-adapter/src/main/java/com/bitark/adapter/controller/ReadStatusController.java`

---

✅ 文档完成，可进入下一阶段。
