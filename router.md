# Project Plan: Distributed In-Memory Read Service (Middleware Core)

> **项目定位**：从零构建一个**分布式、高性能、强可靠**的“已读服务”中间件。
> **核心架构**：
> 1. **存储层**：RoaringBitmap (内存) + WAL (持久化) + Snapshot (快照)。
> 2. **集群层**：自研 NameServer (元数据管理) + Smart Client (客户端分片)。
> 3. **通信层**：基于 gRPC/Netty 的长连接通信。

---

## 📅 Phase 1: 核心存储引擎 (The Storage Kernel)
**目标**：构建一个单机版、掉电不丢数据、读写性能极致的存储黑盒。

### 1.1 WAL 日志子系统 (Persistence)
- [x] **写入器 (`WalWriter`)**: 
  - 基于 NIO `FileChannel`，堆外内存缓冲，支持 Group Commit。
  - **多文件滚动 (Log Rolling)**: 单文件固定大小 (e.g. 1GB)，写满自动滚动，便于过期清理。
- [ ] **恢复器 (`WalReader`)**:
  - 启动时重放日志，校验 CRC32，自动截断 (Truncate) 末尾脏数据，精准定位写入位点。
- [ ] **检查点 (`Checkpoint`)**:
  - 定时将内存全量数据 Dump 为快照文件。
  - 快照完成后，异步删除旧的 WAL 文件，防止磁盘爆炸。

### 1.2 内存状态机 (In-Memory Engine)
- [ ] **数据结构优化**:
  - 核心存储：`ConcurrentHashMap<Long /*UserId*/, RoaringBitmap>`。
  - 采用 **RoaringBitmap** 替代 JDK BitSet，解决稀疏数据内存占用过大问题。
- [ ] **并发控制**:
  - 读写分离设计，确保高并发下 `markRead` (写) 不阻塞 `isRead` (读)。

### 1.3 服务端通信层 (Remoting Server)
- [ ] **RPC 接口定义**:
  - 使用 **Protobuf** 定义紧凑的二进制协议。
  - `rpc MarkRead(MarkReq)`: 极速写入。
  - `rpc IsRead(QueryReq)`: 内存直读。
- [ ] **Server 实现**:
  - 启动 **gRPC Server** (Netty Backend)。
  - 维护 TCP 长连接，避免 HTTP 短连接的握手开销。

---

## 🧠 Phase 2: 自研集群元数据 (The Metadata/NameServer)
**目标**：不依赖 Zookeeper/Etcd，实现轻量级的服务注册与发现 (类似 RocketMQ NameServer)。

### 2.1 NameServer 实现
- [ ] **轻量级注册中心**:
  - 一个独立的 Java 进程，无状态，可集群部署。
  - 维护全局路由表：`Topic/Table -> Map<ShardId, List<NodeAddr>>`。
- [ ] **RPC 接口**:
  - `RegisterBroker`: Broker 启动/心跳时上报自身 IP、Port、负责的 ShardId。
  - `GetRoute`: Client 拉取路由信息。
- [ ] **存活检测**:
  - 扫描路由表，剔除超过 30s 未发送心跳的 Broker。

---

## 🌐 Phase 3: 客户端与路由 (Smart Client & Routing)
**目标**：将路由逻辑下沉到客户端 SDK，实现无中心化的高性能访问。

### 3.1 客户端 SDK (Java)
- [ ] **路由缓存与更新**:
  - SDK 启动时连接 NameServer，拉取全量路由表并缓存到本地。
  - 开启定时任务 (e.g. 30s) 刷新路由。
- [ ] **本地分片路由**:
  - 实现 Hash 算法：`targetNode = RouteTable.get( Hash(userId) % TotalShards )`。
  - **No-Hop 直连**: 客户端直接与目标 Broker 建立 gRPC 连接，不经过任何网关转发。

---

## 🛡️ Phase 4: 高可用主备架构 (HA & Replication)
**目标**：通过异步数据复制，实现节点级容灾。

### 4.1 主备数据同步
- [ ] **架构模式**: `Master-Slave` 异步复制。
- [ ] **同步流程**:
  - Master 处理完写请求，返回 Client 成功。
  - Master 后台线程批量读取 WAL，通过 gRPC 推送给 Slave。
  - Slave 接收 LogEntry -> 写本地 WAL -> 重放进本地内存。

### 4.2 故障处理 (Failover)
- [ ] **客户端降级**:
  - SDK 检测到 Master 连接断开/超时。
  - 自动切换读取同分片下的 Slave 节点 (保证可读)。
- [ ] **主备切换 (进阶)**:
  - 配合 NameServer 实现自动选主 (Slave 晋升为 Master)。

---

## 📚 核心技术栈 (Hardcore Stack)

- **Language**: Java 17+
- **Transport**: **gRPC (Based on Netty)** - 高性能长连接 RPC。
- **Storage**: **FileChannel (Direct I/O)** + **RoaringBitmap** - 核心存储壁垒。
- **Serialization**: **Protobuf** - 极致序列化性能。
- **Architecture**: **Share-Nothing** + **Smart Client** - 典型中间件架构。

---

## 📝 研发纪律
1. **Crash Safe**: 任何时刻 `kill -9`，重启后数据必须能通过 WAL + Snapshot 100% 恢复。
2. **Zero Waste**: 内存是瓶颈，严格控制对象创建，尽量复用 Buffer。
3. **No Magic**: 不引入 Spring/Tomcat 等重型框架，main 函数直接启动，保持轻量。