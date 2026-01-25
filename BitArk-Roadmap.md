## BitArk 分布式已读服务演进路线

> 本文是 BitArk 的**架构演进路线图**，从单机 WAL 引擎到具备复制、自愈、故障切换能力的高可用已读服务。
> 目标是：**一步一个能力点，每一阶段都可独立运行、可验证、可回滚。**

---

### 0. 项目目标与设计哲学

- **业务目标**
  - 面向亿级用户、百亿级消息的**已读状态服务**。
  - 要求：**微秒级读**、**强/单调一致性**、**高可用不丢数据**。

- **设计哲学**
  - **计算与 IO 分离**：内存 Bitmap 负责查询，WAL 负责持久化。
  - **无锁化设计**：读路径尽量无锁，写路径控制好并发模型。
  - **日志结构化**：所有状态变化都通过 WAL 记录，Snapshot 只是“缓存”。

- **技术栈约束**
  - 基于 **Spring / Spring Boot** 做组装与配置管理。
  - 配置与逻辑解耦：`application.yml` + `@ConfigurationProperties`。

---

## 阶段 0 → 1：单机内核（已完成）

> **目标**：先证明“本机断电不丢 + 可快速恢复 + 磁盘不会爆”。

- **能力目标**
  - WAL 顺序写入（分段 + group commit）。
  - Snapshot + Checkpoint + **增量回放**恢复内存状态。
  - 基于 Checkpoint 的 WAL GC（只保留快照之后必要的 WAL）。

- **关键抽象**
  - **LSN（位置）雏形**：  
    - 使用 `(segmentIndex, offset)` 表示 WAL 中的逻辑位置。  
    - Checkpoint 记录：`WalCheckpoint(segmentIndex, offset)`。

- **验证方式**
  - 写入若干已读记录 → 重启进程 → 自动恢复后检查 `isRead()`。
  - 多次快照 → 验证旧 WAL 段是否被 GC，磁盘空间不会无限增长。

- **当前状态**
  - 本阶段已完成，BitArk 作为**单机存储引擎**已经具备可靠性基础。

---

## 阶段 1 → 2：基础主从复制（有进度的复制）

> **目标**：从“通知型 HTTP 同步”升级为**有进度感知的复制协议**。

- **问题现状**
  - 当前：Master 在 `read()` 时直接发 HTTP 给 Slave 做同步，只是“通知”，没有复制进度概念。
  - 缺失：
    - Master 不知道 Slave 追到哪。
    - GC 时也无法判断“删这段 WAL，会不会把 Slave 未来需要的日志删掉”。

- **要引入的概念**
  - **LSN**：  
    - 正式将 `(segmentIndex, offset)` 封装为一个 LSN（可用 `WalCheckpoint` 或 `long`）。
  - **ackLSN（确认进度）**：
    - 表示 Slave 已 **持久化并应用** 的最远 WAL 位置。
  - **lastAckLSN（Master 眼中的 Slave 进度）**：
    - Master 内存中为每个 Slave 记录的 ackLSN。

- **最小实现方案（MVP）**
  - **Slave 侧**（接收复制）：
    - 内部同步接口：接收一个或一条 `LogEntry`，写入本地 WAL → 应用到 Bitmap → 返回当前 `ackLSN`。
  - **Master 侧**（发送复制）：
    - `ReadServiceImpl` 写完本地 WAL 之后，从 `WalEngine` 获取当前写入的 LSN。
    - 调用同步接口时，携带这条记录及其 LSN。
    - 收到响应后，更新该 Slave 的 `lastAckLSN`。
  - **存储位置**：
    - 初始阶段：`lastAckLSN` 先只存内存即可（重启后重建）。

- **完成后的收益**
  - Master 能明确知道：**每个 Slave 至少追到了哪一条 WAL**。
  - 为后续的 GC、补发、可靠复制打下基础。

---

## 阶段 2 → 2.5：发送缓冲区 & 基本流控（保护 Master）

> **目标**：防止复制压力拖垮 Master 内存或写入线程。

- **问题场景**
  - Master 写入 QPS = 50,000，Slave 磁盘 / 网络吞吐只有 10,000。
  - 如果 Master 无限制往 Replicator 的队列里塞任务，队列可能无限增长，导致：  
    - 内存爆（OOM / Full GC）；  
    - 写线程阻塞在复制逻辑中，整体服务吞吐被拖慢甚至打挂。

- **设计原则**
  - **保护 Master 优先**：  
    - 不让单个慢 Slave 拖垮整个集群。  
    - 允许 Slave “掉队”，稍后通过 Full Sync 补齐。

- **最小实现**
  - 为每个 Slave 配置一个**有界发送缓冲区**：
    - 如 `BlockingQueue<ReplicationTask>`，容量 N（如 10_000）。
  - 入队策略：
    - 若队列未满：正常入队，Replicator 线程异步发送。
    - 若队列已满：
      - 标记该 Slave 为 `SLOW`/`DEAD`；
      - 暂停给它发送新的增量；
      - 后续由更高层（Full Sync）负责让其追上。

- **完成后的收益**
  - 在高 QPS 场景下，Master 始终可控，不会因为复制拖死自己。
  - 为后续“慢 Slave 走 Full Sync”提供判断基础。

---

## 阶段 2.5 → 3：可靠异步复制（重试 + 防重）

> **目标**：在网络抖动、ACK 丢失、请求重试的情况下，保证复制最终正确。

- **问题场景**
  - ACK 丢失：Master 重试发送同一批数据。
  - 网络错误：部分 batch 发出去但响应丢失，或重试导致重复应用。

- **关键状态**
  - **Master**：
    - `writtenLSN`：本地 WAL 写到哪。  
    - `lastAckLSN[slave]`：每个 Slave 确认应用到哪。
  - **Slave**：
    - `lastAppliedLSN`：本地已经持久化并应用的最远位置。

- **最小实现**
  - Master：
    - Replicator 对发送失败的 batch 做重试（带 backoff）。
  - Slave：
    - 收到某 batch 时：
      - 如果 `batch.startLSN <= lastAppliedLSN`，则认为这部分是重复数据：
        - 直接跳过已经应用的前半段，仅从未应用的部分开始。
  - 业务层：
    - 由于已读标记操作是幂等的（重复设为已读无害），可大大降低设计复杂度。
    - 但仍建议通过 LSN 保留防重框架，为未来非幂等操作预留空间。

- **完成后的收益**
  - 即使网络抖动、重试混乱，最终状态仍然依赖 LSN 有序推进，保持正确。

---

## 阶段 3 → 4：心跳与僵死检测（自我保护）

> **目标**：避免“僵尸 Slave”长期不响应，却一直被 Master 当活着，从而阻塞 GC 和管理决策。

- **问题场景**
  - 某个 Slave 实际已经挂掉或无限慢，但 Master 一直等待其 ACK，从而：
    - 不敢 GC WAL；
    - 以为自己有 N 副本，但实际上只有 1 副本。

- **最小实现**
  - Slave：
    - 定期发送心跳：`heartbeat(lastAppliedLSN)`。
  - Master：
    - 维护 `lastSeenTime[slave]`；
    - 超过配置阈值（如 30s、60s）未收到心跳 → 标记为 `DEAD`。
    - 计算 GC 阈值（minAckLSN）时，仅考虑 `ALIVE` 的 Slave。

- **完成后的收益**
  - 系统具备基本的“自我保护能力”，不会被僵尸节点拖垮。

---

## 阶段 4 → 5：Full Sync / Bootstrap（全量 + 增量）

> **目标**：解决“新 Slave 加入”与“落后太久的 Slave 无法通过增量追上”的问题。

- **问题场景**
  - Master 的 WAL 只保留最近一段（有 GC）。  
  - 一个新的 Slave 加入，或某个旧 Slave 落后太久，请求从很早的 LSN 开始拉数据 → Master 已经没有这段了。

- **核心思想**
  - **本机恢复 = Snapshot + WAL 增量**  
  - **跨机恢复 = Snapshot + WAL 增量**  
  → 复用现有的单机恢复能力，只是 Snapshot 文件来自 Master 而已。

- **最小实现**
  - Master：
    - 维护 `minRetainedLSN`：当前仍然保留的最早 WAL 位置。
  - Slave 发起拉取请求：`pullFromLSN(requestLSN)`：
    - 若 `requestLSN >= minRetainedLSN`：走增量复制。
    - 若 `requestLSN < minRetainedLSN`：返回 `NEED_FULL_SYNC(snapshotLSN, meta)`。
  - Full Sync 流程：
    - Slave 下载 Snapshot 文件；
    - 用 Snapshot 恢复内存状态；
    - 将 `lastAppliedLSN = snapshotLSN`；
    - 再走增量复制补齐后面的 WAL。

- **完成后的收益**
  - 支持新节点加入、老节点追赶，不必无限保留历史 WAL。

---

## 阶段 5 → 6：集群级 GC 策略（从单机正确到集群正确）

> **目标**：在考虑 Slave 进度的前提下安全 GC，同时控制 WAL 保留成本。

- **现有能力**
  - 单机：基于 Checkpoint/LSN 做 GC，保证恢复能力不受影响。

- **扩展到集群**
  - Master 需要在如下维度间平衡：
    - 数据安全：不能删掉活跃 Slave 还没拿到的 WAL。
    - 资源成本：不能无限保留所有历史 WAL。

- **可能策略**
  - **保守模式**：
    - GC 只能删到 `min(所有 ALIVE Slave 的 lastAckLSN, 最新快照 LSN)`。
    - 简单安全，但 WAL 可能增长较快。
  - **工业模式**（推荐）：
    - 为每个 Slave 设定“最大允许落后窗口”（时间 / LSN 距离）。
    - 超出窗口的 Slave 标记为 `OUTDATED`，强制走 Full Sync 或人工干预。
    - GC 只保障对“正常 Slave”的增量能力，不为严重落后节点无限背锅。

---

## 阶段 6 → 7（选修）：Failover / Epoch / Fencing（自动选主准备）

> **目标**：从 Static Master-Slave 迈向真正的“高可用集群”，Master 挂了能自动切主且不会产生“双主写入”。

- **关键问题**
  - Slave 升级为新 Master 后，如果旧 Master“诈尸”复活并继续接收写入，将导致数据分叉。

- **必要抽象**
  - **Epoch / Term（任期）**
    - 每一次“选主”都会产生新的 EpochID。
    - 所有 WAL Segment、写请求都要带 EpochID。
  - **Fencing Token（隔离令牌）**
    - 新 Master 拥有最新 Epoch；
    - 旧 Master 恢复后发现自己 Epoch 落后，主动只做只读 / 退役。

- **实现时机**
  - 这是复制系统之上的“控制平面”能力，应该在：
    - **复制管道稳定 + LSN 机制完整 + 心跳/GC 策略成型之后** 再考虑。

---

## 横切增强：日志校验（CRC）与读一致性策略

### A. WAL CRC 校验（存储安全底裤）

- **为什么**
  - 磁盘坏道、内存翻转、网络错误都可能悄悄篡改数据。

- **做什么**
  - 为每条 WAL record 增加 CRC32：
    - 写入时计算并存储。
    - 读取 / 复制时重新计算，CRC 不一致直接报错 / 丢弃 / 触发重传。

- **位置**
  - 属于 WAL 子系统，可以独立插入，不影响对外行为。

### B. 读一致性（业务访问策略）

- **问题**
  - Master LSN=100，Slave LSN=90，如果客户端读 Slave 会看到旧数据。

- **策略（存储层 + 业务层配合）**
  - 简单阶段：所有读写都走 Master，暂不提供 Slave 读，规避问题。
  - 高级阶段：
    - 写返回时附带 LSN；
    - 读 Slave 时带 `minLSN`；
    - Slave `appliedLSN < minLSN` 时选择：
      - 阻塞等追上（强一致读）；  
      - 或返回“请读 Master”（单调读保障）。

---

## 当前进度与下一步

- **当前状态**
  - 阶段 0 → 1（单机 WAL + Snapshot + Checkpoint + GC）已完成。
  - 架构分层（adapter / engine / infrastructure / common）已理顺，并通过 Spring 配置化。

- **下一步唯一聚焦点**
  - 进入 **阶段 1 → 2：基础主从复制（ackLSN + lastAckLSN）**：
    - 在现有 HTTP 同步基础上，增加复制进度的传递与记录；
    - 让 Master 能回答：“这个 Slave 至少追到了哪条 WAL（LSN）？”
