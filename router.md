# Project Plan: High-Performance Distributed Read Service

> **项目目标**：构建一个基于内存、持久化、高吞吐、低延迟的分布式“已读服务”引擎。  
> **核心架构**：LSM-style WAL (Write-Ahead Log) + In-Memory Bitmap + AP Model Clustering (最终一致性)。

---

## 📅 Phase 1: 单机核心引擎 (Current Focus)
**目标**：实现一个 Crash-Safe（崩溃安全）的单机存储引擎。确保进程重启后，通过重放日志恢复内存状态。

### 1.1 完善 WAL 写入 (Done/Refine)
- [x] **`WalWriter_V2`**: 基于 NIO `FileChannel` + 堆外内存 + 组提交 (Group Commit) + 异步回调。
- [ ] **Refactor**: 修改构造函数，支持传入 `initPosition`（初始写入偏移量），避免重启覆盖旧数据。

### 1.2 实现 WAL 读取与恢复 (Priority High)
- [ ] **`WalReader`**:
    - **遍历逻辑**：从文件头开始读取，解析 `[Length][Body][CRC]` 格式。
    - **校验逻辑**：校验 CRC32，确保数据未损坏。
    - **截断逻辑 (Truncate)**：遇到文件尾部的半条残损日志（因断电导致），自动截断并忽略，返回有效的 `EndOffset`。
    - **接口定义**：`long replay(String path, LogEntryHandler handler)`。

### 1.3 内存状态机 (State Machine)
- [ ] **`ReadStatusEngine`**:
    - **数据结构**：引入 **RoaringBitmap** (推荐) 或使用 JDK `BitSet`。
    - **存储模型**：`ConcurrentHashMap<Long /*UserId*/, RoaringBitmap /*MessageIds*/>`。
    - **业务逻辑**：
        - `apply(LogEntry entry)`: 幂等更新内存。
        - `isRead(long userId, long messageId)`: 内存查询。

### 1.4 引擎组装与启动流程
- [ ] **`ReadServiceServer`** (Bootstrap):
    1. 初始化内存引擎 `new ReadStatusEngine()` (此时为空)。
    2. 初始化 `WalReader` -> 调用 `replay()` 回放历史数据填入引擎。
    3. 获取回放结束的 `lastValidOffset`。
    4. 初始化 `WalWriter(lastValidOffset)` -> 准备接收新写入。
    5. **验证测试**：编写 Integration Test，模拟写入 -> 杀进程 -> 重启 -> 查询数据是否存在。

---

## 🚀 Phase 2: 网络化与 RPC 接口
**目标**：将单机引擎封装为网络服务，支持远程调用，为集群化做准备。

### 2.1 定义通信协议
- [ ] **Protocol**: 使用 **Protobuf** 定义请求/响应包（更紧凑，适合高性能场景）。
    - `MarkReadRequest { int64 user_id; int64 message_id; }`
    - `QueryReadRequest { int64 user_id; int64 message_id; }`

### 2.2 网络层实现
- [ ] **Server**: 引入 **Netty**。
    - 建立 TCP Server。
    - 编写 `Codec` 处理粘包/拆包。
    - `Handler` 层调用 Phase 1 的 Engine 处理业务。
- [ ] **Client SDK**: 封装 Java Client，提供 `markRead()` 和 `isRead()` 阻塞/异步方法。

---

## 🌐 Phase 3: 弱一致性集群 (Distributed AP Model)
**目标**：支持水平扩展（Sharding）和高可用（Replication），采用无主或异步主从复制，允许短暂的数据不一致。

### 3.1 数据分片 (Sharding)
- [ ] **路由策略**：
    - 实现简单的客户端路由或 Proxy 层。
    - 算法：`Hash(UserId) % NodeCount` 或一致性哈希。
    - **效果**：不同的用户数据分布在不同的机器上，突破单机内存瓶颈。

### 3.2 异步复制 (Replication)
- [ ] **Master-Slave / Peer-to-Peer 架构**：
    - 每个分片配置 1 Master + N Slaves。
    - **写流程**：Client -> Master 写 WAL + 内存 -> **立刻返回 OK** (保证极低延迟)。
    - **同步流程**：Master 后台线程持续将新增的 WAL Log 推送给 Slave。
    - **Slave 流程**：收到 Log -> 写本地 WAL -> 更新本地内存。

### 3.3 故障转移 (Failover) - *MVP版*
- [ ] **切换机制**：
    - 当 Master 宕机，客户端或者协调组件感知。
    - 能够降级读取 Slave，或者将 Slave 提升为新 Master（注意处理数据丢失问题）。

---

## 🛠 Phase 4: 工程化与生产级特性 (Engineering Polish)
**目标**：优化性能，增加可维护性，防止随着时间推移数据量爆炸。

### 4.1 快照机制 (Snapshotting)
- [ ] **Snapshot Writer**:
    - 后台定时任务（如每 10 分钟）。
    - 将内存中的 `Map<UserId, RoaringBitmap>` 序列化存储到 `snapshot.bin`。
- [ ] **Log Truncation**:
    - 快照生成成功后，安全删除对应位点之前的旧 WAL 文件。
- [ ] **Fast Recovery**:
    - 重启逻辑优化：先加载 `snapshot.bin`，再回放少量的增量 WAL。

### 4.2 监控与指标 (Observability)
- [ ] **Metrics**:
    - 集成 Prometheus / Micrometer。
    - 关键指标：`write_latency`, `wal_size`, `qps`, `memory_usage`, `replication_lag`。
- [ ] **Health Check**: 提供 `/health` 接口。

---

## 📚 技术栈建议 (Tech Stack)

- **Language**: Java 17+ / 21
- **Core IO**: `java.nio.channels.FileChannel` (读写分离，读可考虑 mmap)
- **Memory Structure**: `org.roaringbitmap:RoaringBitmap` (高效压缩位图)
- **Networking**: `Netty` 4.x
- **Serialization**: `Protobuf` (推荐) 或 `Hessian`
- **Logging**: `Slf4j` + `Logback`
- **Testing**: `JUnit 5` + `JMH` (基准测试)

---

## 📝 开发原则 (Principles)

1. **Crash Safe First**: 所有的优化前提是不丢数据（除非显式配置为异步落盘）。
2. **Memory Efficiency**: 这是一个内存密集型应用，关注对象分配，减少 GC 压力。
3. **Keep It Simple**: 在 Phase 3 之前，不要引入 Zookeeper/Etcd 等外部依赖，先用静态配置跑通逻辑。