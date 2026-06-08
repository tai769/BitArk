# BitArk 系统整体架构设计

> 本文是 BitArk 的**系统架构总纲**，定义工业级分布式已读服务的完整能力目标、现状评估、演进里程碑与验收标准。
> 与 `BitArk-Roadmap.md`（阶段演进路线）配合使用：Roadmap 管"怎么做"，本文管"做到什么程度"。

---

## 一、项目定位

BitArk 是一个面向**亿级用户、百亿级消息**的纯内存分布式"已读状态服务"中间件。

核心壁垒：

```text
RoaringBitmap（内存查询，微秒级） + WAL（顺序写持久化，Crash-Safe） + Snapshot/Checkpoint（秒级恢复）
```

设计哲学：

1. **计算与 IO 分离**：内存 Bitmap 负责查询，WAL 负责持久化
2. **无锁化设计**：读路径尽量无锁，写路径控制好并发模型
3. **日志结构化**：所有状态变化都通过 WAL 记录，Snapshot 只是"缓存"

---

## 二、工业级能力全景（10 个能力域）

一个真正工业级的分布式纯内存已读服务，必须在这 10 个能力域都成立。按"地基 → 骨架 → 外延"排列：

| # | 能力域 | 工业级标准 |
|---|--------|-----------|
| 1 | 数据模型与一致性契约 | 明确 CRDT(G-Set) 语义；WAL record 带 leaderLsn+epoch；可配 W/R 一致性级别；单调读 |
| 2 | 单机存储内核 | WAL(CRC+末尾截断) + 一致性 Snapshot(CoW) + Checkpoint + GC；Roaring64 支持百亿 msgId |
| 3 | 内存容量管理 | 单机内存有上限——容量规划、冷热/过期、超大用户分桶、水位告警 |
| 4 | 复制与持久性 | Pull 增量 + Full Sync 闭环 + ISR + quorum-ack 控制"不丢"；anti-entropy 位图兜底补偿 |
| 5 | 高可用/故障切换 | 控制面选主 + epoch/fencing；单节点挂不影响对外；挂掉节点恢复后可自动追平回归 |
| 6 | 集群分片与扩容 | slot 虚拟槽 + 控制面路由表(带版本) + 在线扩缩容 slot 迁移(rebalance) |
| 7 | 多业务隔离 | bizId/namespace 维度贯穿 API → WAL payload → 内存结构 |
| 8 | 通信层 | 对外 + 内部复制走 gRPC/Netty 长连接 + protobuf + 批量 |
| 9 | 客户端 SDK | 路由缓存+定时刷新、主挂自动降级读从、重试/超时/连接池 |
| 10 | 可观测性 & 运维 & 质量 | 指标(QPS/P99/lag/ISR/mem/GC)、限流背压、优雅启停、混沌测试、全链路压测 |

---

## 三、现状评估

逐域标注（✅ 已完成 / 🟡 半成品 / ❌ 缺失）：

| # | 能力域 | 状态 | 说明 |
|---|--------|------|------|
| 1 | 一致性契约 | 🟡 | leaderLsn/epoch 字段已预留，但 leaderLsn 仍用 lsn/fileSize 估算物理位置（应纯逻辑序号 + WalIndex 映射） |
| 2 | 单机存储内核 | 🟡 | WAL/Snapshot/Recovery/GC 链路通了，但 CRC+truncate 未接到新 WalRecord、Snapshot 的 CoW 一致性是半成品、msgId 强转 int 会溢出 |
| 3 | 内存容量管理 | ❌ | 整块缺失，无容量上限、无淘汰/过期、无超大用户处理 |
| 4 | 复制与持久性 | 🟡 | Pull 骨架在但 readBatch 未实现、Full Sync 返回 null、无 quorum-ack（写完本地直接 ack，单机挂就丢） |
| 5 | 高可用/切换 | ❌ | 静态 master-slave 写死，无选主、无 fencing、client 不会降级 |
| 6 | 分片扩容 | 🟡→❌ | ShardingStrategy 是固定取模、扩容全量 rehash、分片线和复制线没打通 |
| 7 | 多业务隔离 | ❌ | ReadStatusEngine 只有 Map<userId, bitmap>，无 bizId |
| 8 | 通信层 | 🟡 | 全 HTTP/RestTemplate/JSON |
| 9 | 客户端 SDK | 🟡 | 有静态路由，无刷新、无降级、无重试 |
| 10 | 可观测性/质量 | ❌ | 只开了 actuator 基础端点；测试是手写 main |

**一句话总结**：#1、#2、#4 的"单机 + 复制地基"打到了 70%，但 #3 内存容量、#5 故障切换、#6 真扩容、#7 多业务、#10 质量压测这 5 块基本是空的——而这 5 块恰恰是"从玩具到工业级"的分水岭。

---

## 四、演进里程碑（M0 ~ M7）

### 总览

```text
M0 收口地基 → M1 持久性正确 → M2 复制自愈 → M3 控制面+故障切换
    → M4 真分片+扩容 → M5 多业务隔离 → M6 内存容量治理 → M7 性能终局
```

节奏划分：

```text
M0~M2：核心能跑（单机 → 不丢 → 自愈）
M3~M4：高可用 + 扩容（切主 → 分片）
M5~M7：工业化打磨（多租户 → 容量治理 → 性能终局）
```

核心原则：**每一步交付一个可断言的能力，不是一个功能点。上一步能力不断言通过，不进下一步。**

---

### M0 — 收口地基（止血）

**目标**：项目重新编译通过，单机链路跑通，Pull 增量全链路打通。

| 任务 | 说明 |
|------|------|
| WalRecord 迁移收尾 | LogEntry → WalRecord 统一，已读 payload 编解码独立 |
| readBatch + WalIndex 打通 | append 时建 WalIndex，用 ceiling 实现 readBatch |
| Roaring64 修溢出 | msgId 从 int 改 long，换 Roaring64NavigableMap 或分桶 |
| CRC + 脏数据截断 | WalReader 重放时校验 CRC，自动 truncate 末尾半截 record |
| 硬编码路径修正 | /home/qiushui → 配置化 |
| JDK 版本对齐 | pom.xml Java 11 → 17（Spring Boot 3.x 要求） |

**验收标准**：

```text
□ 单节点启动 → 写入 → kill -9 → 重启 → 自动恢复 → isRead 正确
□ Master + Slave 两个节点，增量复制闭环：Master 写入 → Slave 拉取 → Slave isRead 正确
□ Slave 重启 → 从进度文件继续追 → 数据完整
```

---

### M1 — 持久性正确（不丢）

**目标**：已 ack 的写，任意单点 kill -9 后 100% 可查。

| 任务 | 说明 |
|------|------|
| quorum-ack | 写到 ISR 多数派才 ack 给客户端（可配 W=1/2/多数） |
| Snapshot 真 CoW 一致性 | Active/Frozen 双层状态，freeze 短临界区切换，锁外 dump |
| GC 等 quorum 水位 | GC 只能删到 min(ISR 成员 lastAckLsn, 最新快照 LSN) |

**关键设计约束**：

```text
I1: WAL 先于内存状态更新（先 append WAL，再 markRead）
I2: WAL append + state apply 对 freeze 表现为原子
I3: frozenStatus 只读（freeze 后写路径不能再修改）
I4: snapshotBytes 与 snapshotLeaderLsn 严格对齐
I5: 读路径必须覆盖所有有效状态层（active + frozen）
```

**验收标准**：

```text
□ kill -9 Master，已 quorum-ack 的写零丢失
□ kill -9 Slave，Master 侧无影响，Slave 恢复后继续追
□ Snapshot 期间前台写入不阻塞、不丢数据
```

---

### M2 — 复制自愈闭环

**目标**：新节点加入 / 落后节点可自动追平，无需人工干预。

| 任务 | 说明 |
|------|------|
| Full Sync 落地 | Master dump 快照 + snapshotLsn → Slave 下载 → loadSnapshot → progress 推进 → 继续增量 |
| 断代自动触发 | fetch() 发现 fromLsn < earliestRetainedLsn → 自动走 Full Sync |
| anti-entropy 兜底 | 周期性位图对账，补偿极端场景下的遗漏 |

**验收标准**：

```text
□ 新加一个 Slave → 自动触发 Full Sync → 全量加载 → 增量追到 ISR
□ Slave 落后太多 → WAL 已 GC → 自动走 Full Sync → 追平后回归 ISR
□ 全链路无人工干预
```

---

### M3 — 控制面 + 故障切换

**目标**：Master 挂了集群秒级切主继续对外，旧 Master 复活自动退只读。

| 任务 | 说明 |
|------|------|
| 控制面/数据面切分 | 先简单 NameServer，接口按"将来换强一致后端"设计 |
| 选主协议 | 基于 epoch/term 的轻量选主 |
| epoch + fencing | 所有 WAL record 带 epoch；旧 Master 复活发现 epoch 落后 → 自动退化只读 |
| Client 自动降级 | Master 不可达 → 自动读 Slave（单调读保障） |

**关键约束**：

```text
- epoch 是格式/协议级的，必须在设计阶段定好，不能后补
- 控制面接口必须按"将来替换后端"设计（当前内存实现 → 将来 etcd/raft）
- fencing token 必须写入 WAL，否则无法阻止旧 Master 诈尸写入
```

**验收标准**：

```text
□ kill Master → Slave 秒级升主 → 集群对外读写不中断
□ 旧 Master 恢复 → 发现 epoch 落后 → 自动退只读 → 不产生双写
□ Client 自动感知主从切换 → 无需重启
```

---

### M4 — 真分片 + 在线扩容

**目标**：支持多节点水平扩展，扩缩容不停服不丢数据。

| 任务 | 说明 |
|------|------|
| slot 虚拟槽 | hash(userId) % SLOTS → slot → node 映射 |
| 路由表带版本 | 控制面维护路由表，版本号递增，Client 缓存 + 定时刷新 |
| 每 shard 一组主从 | 分片线和复制线打通：每个 slot 独立的 Master/Slave 组 |
| 在线 slot 迁移 | 扩容时按槽迁移，不全量 rehash |

**验收标准**：

```text
□ 3 节点集群 → 请求按 slot 路由 → 数据均匀分布
□ 扩容第 4 节点 → slot 迁移 → 迁移期间服务不中断
□ 缩容节点 → slot 回收 → 数据不丢
```

---

### M5 — 多业务隔离

**目标**：多个业务方共用同一套服务，数据完全隔离。

| 任务 | 说明 |
|------|------|
| 内存结构改造 | Map<bizId, Map<userId, bitmap>> |
| WAL payload 增加 bizId | 编解码格式扩展 |
| API 增加 bizId 参数 | 所有读写接口带 bizId |
| Snapshot / Full Sync 按 bizId 维度 | 序列化格式支持 |

**验收标准**：

```text
□ 两个 bizId 各自写入 → 互不干扰 → 分别查询正确
□ Snapshot / Full Sync 按 bizId 维度正确恢复
```

---

### M6 — 内存容量治理

**目标**：单机内存有上限，到阈值有可控行为，不 OOM。

| 任务 | 说明 |
|------|------|
| 容量上限 | 可配最大内存阈值，到达后拒绝写入 / 淘汰冷数据 |
| 冷热分离 | 不活跃用户的 bitmap off-heap 或落盘 |
| 超大用户分桶 | 单个 userId 的 bitmap 太大时分桶存储 |
| 水位告警 | 内存使用率监控 + 告警 |

**验收标准**：

```text
□ 内存到阈值有可控行为（拒绝写入或淘汰），不 OOM
□ 冷数据自动 off-heap / 落盘，热数据仍在内存
□ 超大用户不影响其他用户查询性能
```

---

### M7 — 性能终局 + SDK + 可观测

**前置条件**：M0~M6 全部完成，功能闭环，全量测试通过。

| 任务 | 说明 |
|------|------|
| 通信层升级 | HTTP → gRPC/Netty 长连接 + Protobuf（内部复制 + 对外接口） |
| 客户端 SDK 健全 | 路由缓存+定时刷新、主挂自动降级、重试/超时/连接池 |
| 读一致性策略 | 写返回带 leaderLsn，读 Slave 带 minLeaderLsn，落后则阻塞或回退读 Master |
| 全套指标 | QPS、P99 延迟、WAL lag、复制 lag、ISR 成员数、GC 次数、内存占用 |
| 混沌测试 | kill -9、网络分区、慢 slave、磁盘满 |
| 长稳压测 | 24h 运行，验证 WAL GC 稳定、内存无泄漏、无 Full GC 长停顿 |

**验收标准**：

```text
□ 全量回归测试通过
□ 压测对比 HTTP vs gRPC 的 QPS/P99 提升
□ 达成 SLO 目标（见下文）
□ 24h 长稳无异常
```

---

## 五、SLO 目标与压测矩阵

### SLO 目标（初版基线，按机器调）

| 指标 | 目标 |
|------|------|
| 写 P99 | group commit 下 < 2~5ms（含 fsync）；纯内存 markRead < 50μs |
| 读 P99 | 内存直读 < 100μs（单调读放宽到 < 1ms） |
| 单机写吞吐 | ≥ 5 万 QPS（group commit 批量） |
| 单机读吞吐 | ≥ 50 万 QPS |
| 内存效率 | 记录"1 亿已读 bit 实际占多少 MB"曲线 |
| RTO 恢复时间 | 1 亿条记录 < 30s |
| 数据零丢失 | quorum-acked 的写，任意单点 kill -9 后 100% 可查 |
| 可用性 | 单节点宕机，对外读写不中断 |

### 压测/混沌矩阵（M2 起持续跑）

| 场景 | 验证内容 |
|------|---------|
| 单机写吞吐 | 验证 group commit、buffer、fsync 批量效果（QPS vs 延迟曲线） |
| 单机读吞吐 | 并发 isRead，P50/P99/P999 |
| 内存容量曲线 | 数据量 1千万 → 1亿 → 10亿，画内存占用 + Roaring 压缩率 |
| 恢复时间 | 不同 WAL/Snapshot 规模下的 RTO |
| 复制延迟 | Master 高 QPS 写，测 Slave lag、ISR 进出抖动 |
| 混沌注入 | ① kill -9 Master ② 网络分区(脑裂) ③ 慢 Slave ④ 磁盘满 |
| 长稳测试 | 24h，验证 WAL GC 稳定、内存无泄漏、无 Full GC 长停顿 |

**关键**：从 M0 就把"压测 + 断言"做成可重复脚本（JMH 单机 + 全链路压测程序），每过一个里程碑重跑同一套，留下数字对比。

---

## 六、不可后补的两件事

在整个演进过程中，有两件事是**格式/协议级的，后补等于重来**，必须在设计阶段固化：

### 1. WAL record 的 leaderLsn + epoch 格式 + 逻辑 LSN / WalIndex 映射

```text
当前痛点：LogEntry → WalRecord 的迁移就是证明——格式一旦定错，后面所有链路都要重写。

固化内容：
  - WalRecord 二进制格式：recordLength | magic | version | leaderLsn | epoch | type | payload | crc32
  - leaderLsn 是纯逻辑单调序号，不与物理文件位置挂钩
  - 物理位置完全由 WalIndex（leaderLsn → WalPosition）映射
  - Slave 原样保存 leaderLsn，不自己分配
```

### 2. 控制面 / 数据面切分的接口骨架

```text
当前痛点：如果控制面后补，分片（M4）和复制（B/C/D）的打通又要重来。

固化内容：
  - 控制面接口定义（节点注册、心跳、路由表查询、选主）
  - 接口按"将来换强一致后端"设计（当前内存实现 → 将来 etcd/raft）
  - 数据面只依赖控制面接口，不直接依赖实现
```

---

## 七、技术栈约束

| 层次 | 技术选型 |
|------|---------|
| 框架 | Spring Boot 3.x（组装与配置管理） |
| 语言 | Java 17+ |
| 内存位图 | RoaringBitmap（Roaring64NavigableMap，支持百亿级） |
| 持久化 | 自研 WAL（Group Commit + FileChannel + CRC32） |
| 网络（当前） | Spring Web / RestTemplate / HTTP + JSON |
| 网络（终局） | gRPC / Netty 长连接 + Protobuf |
| 构建 | Maven 多模块 |

---

## 八、模块结构

```text
BitArk/
├── read-common          // DTO / 日志记录 / 编解码 / 枚举
├── read-infrastructure  // 线程池、配置管理
├── read-engine          // 核心：WAL、Snapshot、Recovery、Replication、ReadStatusEngine
├── read-cluster         // 分片策略 ShardingStrategy / NodeInfo
├── read-adapter         // Spring Boot 启动 + HTTP 控制器
├── read-client          // Smart Client（客户端分片路由）
└── test                 // 集成测试
```

逻辑分层（自上而下）：

```text
┌─────────────────────────────────────────────────┐
│  接口适配层 (read-adapter)                       │
│  对外 API / 内部复制接口 / 协议转换               │
├─────────────────────────────────────────────────┤
│  集群协调层 (read-cluster + read-engine/replication) │
│  分片路由 / 复制状态机 / 选主 / ISR               │
├─────────────────────────────────────────────────┤
│  存储内核层 (read-engine)                        │
│  WAL 引擎 / 内存位图 / Snapshot / Recovery / GC  │
├─────────────────────────────────────────────────┤
│  基础设施层 (read-infrastructure)                │
│  线程池 / 配置管理 / 优雅关闭                     │
└─────────────────────────────────────────────────┘
```

---

## 九、当前最紧迫的两件事

在整个 M0~M7 路线图中，有两个东西是**格式/协议级的，后补等于重来**：

1. **WAL record 的 leaderLsn + epoch 格式** + 逻辑 LSN / WalIndex 映射
   - 当前痛点：LogEntry → WalRecord 的迁移还没收尾，`readBatch` 抛异常
   - 这是 M0 的第一步，也是整个系统的数据格式根基

2. **控制面 / 数据面切分的接口骨架**
   - 当前痛点：分片和复制是两条平行线，没有交叉点
   - 这是 M3 的第一步，也是 M4（分片）和 M5（多业务）的前提

先把这两个格式/接口级的东西固化，其余按里程碑增量补。

---

## 十、一句话路线

```text
先止血收尾让核心跑通（M0）→ 补 quorum-ack 保证不丢（M1）→ 做 Full Sync 实现自愈（M2）
→ 建控制面实现切主（M3）→ 做虚拟槽实现真扩容（M4）→ 加 bizId 多租户隔离（M5）
→ 治内存容量防 OOM（M6）→ 最后换 gRPC + 全套可观测（M7）
```

每一个 M 都是一个**可独立运行、可验证、可回滚**的能力断言。上一个不断言通过，不进下一个。
