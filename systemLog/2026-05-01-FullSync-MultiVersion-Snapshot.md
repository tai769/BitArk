# BitArk Full Sync 多版本快照设计决策

日期：2026-05-01

## 1. 背景：我们遇到了什么问题

BitArk 当前已经从单机 WAL + Snapshot + Checkpoint 演进到 Pull 复制模型。

复制主链路是：

```text
Slave 读取本地 progress
  -> 向 Master 发起 FetchRequest(fromLsn)
  -> Master 从 WAL 读取 batch
  -> Slave 应用 batch
  -> Slave 保存 nextLsn
```

这个模型依赖一个前提：

```text
Slave 需要的 WAL 在 Master 上仍然存在。
```

当 Master 做了 Snapshot + GC 后，旧 WAL 可能已经被删除。如果出现：

```text
Slave fromLsn < Master earliestRetainedLsn
```

说明 Slave 想要的增量日志已经断代，继续 Pull WAL 会丢失中间状态。

因此需要 Full Sync：

```text
Master 生成完整状态快照
Slave 加载完整状态快照
Slave 将 progress 推进到 snapshotLsn
Slave 再从 snapshotLsn 继续 Pull 增量
```

Full Sync 的核心不是“传一个 byte[]”，而是：

```text
snapshotBytes 和 snapshotLsn 必须描述同一个逻辑时刻。
```

如果这两个值错位，会出现两类严重问题：

```text
快照旧，LSN 新：Slave 跳过还没进快照的 WAL，导致数据丢失。
快照新，LSN 旧：Slave 重放已经包含在快照里的 WAL，导致重复应用。
```

虽然当前 markRead 是幂等操作，重复应用短期不致命，但工业级设计不能依赖“碰巧幂等”掩盖快照边界错误。

## 2. 本质问题

这个问题本质是：

```text
在并发写入下，为内存状态机生成一个 point-in-time consistent snapshot，
并且这个 snapshot 必须和 WAL 位置严格绑定。
```

需要同时满足：

```text
1. snapshotBytes 是某一时刻的完整状态。
2. snapshotLsn 是同一时刻的 WAL 进度。
3. snapshotLsn 之前的所有 WAL 都已经体现在 snapshotBytes 中。
4. snapshotLsn 之后的写入不能污染 snapshotBytes。
5. dump 大快照时不能长时间阻塞前台写入。
```

满足前四条可以用大锁，但第五条要求我们不能简单 stop-the-world。

## 3. 技术方案对比

### 方案一：Stop-the-World 全锁

做法：

```text
拿全局写锁
  -> dump 当前内存状态
  -> 读取当前 LSN
  -> 释放锁
```

优点：

```text
实现最简单。
正确性容易证明。
没有多版本生命周期问题。
```

缺点：

```text
锁持有时间等于 dump 时间。
状态大时会阻塞写入数秒甚至更久。
在分布式系统里，Leader 长时间阻塞可能触发超时、误判、切主。
```

适用场景：

```text
状态很小。
写入低频。
对延迟不敏感。
教学型 KV 或早期简单实现。
```

对 BitArk 的判断：

```text
不适合作为目标方案。
可以作为最小正确性方案，但不符合工业级目标。
```

### 方案二：fork + OS Copy-on-Write

代表：

```text
Redis BGSAVE / BGREWRITEAOF
```

做法：

```text
进程 fork
父进程继续写
子进程持有 fork 时刻的内存视图并慢慢 dump
OS 通过页表 Copy-on-Write 保证快照视图稳定
```

优点：

```text
应用层改造少。
锁持有时间短。
利用操作系统能力，性能强。
```

缺点：

```text
Java/JVM 不适合 fork 后继续复杂执行。
JVM 多线程、GC、运行时锁状态会让 fork 后子进程环境复杂且危险。
写热点会触发大量 COW，内存峰值可能接近翻倍。
仅适合 Unix 类系统。
```

适用场景：

```text
C/C++ 单进程内存数据库。
典型代表是 Redis。
```

对 BitArk 的判断：

```text
BitArk 是 Java 项目，直接排除。
```

### 方案三：Active + Frozen 双层状态

代表思想：

```text
RocksDB / LevelDB: Mutable MemTable + Immutable MemTable
HBase: active memstore + snapshot memstore
Cassandra: active memtable + flushing memtable
```

做法：

```text
正常写入只写 activeStatus
Full Sync 触发时做一次短临界区切换：
  frozenStatus = activeStatus
  activeStatus = new empty state
  snapshotLsn = currentAppliedLsn
临界区外慢慢 dump frozenStatus
读取时查 activeStatus，再查 frozenStatus
dump 完成后处理 frozenStatus 生命周期
```

优点：

```text
锁持有时间接近指针切换时间。
dump 大快照时不长时间阻塞前台写入。
与 BitArk 的 bitmap 已读状态天然匹配。
已读状态只增不删，可通过 OR 合并多层状态。
这是 Java 状态引擎中更可落地的工业方案。
```

缺点：

```text
读路径从单层变成多层。
frozenStatus 生命周期需要设计。
同一时间多个 Full Sync 需要排队或扩展为多版本链。
内存峰值在 freeze 后会短期上升。
```

适用场景：

```text
写多读多。
状态可合并。
dump 可能比较慢。
希望后台快照不阻塞前台写入。
```

对 BitArk 的判断：

```text
选择该方案。
这是当前最符合 BitArk 业务和 Java 实现约束的方案。
```

### 方案四：完整 MVCC

代表：

```text
PostgreSQL xmin/xmax
InnoDB undo log + read view
TiKV 多版本 KV
RocksDB sequence snapshot
```

做法：

```text
每次写入都生成新版本。
快照持有一个版本号。
读取时按版本号过滤。
旧版本通过 GC 清理。
```

优点：

```text
天然支持多个并发快照。
支持历史读。
读写并发能力强。
```

缺点：

```text
写路径更重。
版本 GC 复杂。
对 BitArk 当前“只需要一个 Full Sync 快照”的目标过度设计。
```

适用场景：

```text
事务数据库。
需要历史版本查询。
需要多个并发快照。
```

对 BitArk 的判断：

```text
暂不选择。
未来如果支持历史状态查询或事务语义，再考虑。
```

### 方案五：Persistent / Immutable 数据结构

代表：

```text
Clojure persistent collections
Scala immutable collections
Datomic
```

做法：

```text
所有写入返回一个新的结构根节点。
旧版本通过结构共享保留。
快照只需持有旧 root。
```

优点：

```text
快照天然稳定。
读路径无锁。
多版本能力强。
```

缺点：

```text
写入存在路径复制开销。
JVM GC 压力较大。
与 RoaringBitmap 这类压缩 bitmap 结构不自然。
工程实现成本高。
```

对 BitArk 的判断：

```text
不选择。
```

### 方案六：WAL-Only

做法：

```text
不维护状态快照。
Full Sync 时把完整 WAL 发给 Slave 重放。
```

优点：

```text
实现简单。
只有 WAL 一个事实来源。
```

缺点：

```text
WAL 不能安全 GC。
Follower 恢复时间随 WAL 无限增长。
磁盘空间不可控。
```

对 BitArk 的判断：

```text
不选择。
该方案违背 BitArk 的秒级恢复和 WAL GC 目标。
```

### 方案七：Chandy-Lamport 分布式快照

代表：

```text
Flink Checkpoint
Spark Streaming checkpoint
```

做法：

```text
在分布式数据流中注入 barrier。
各算子收到 barrier 后生成本地快照。
通过 barrier 对齐实现全局一致快照。
```

优点：

```text
适合分布式流处理系统。
支持全局一致 checkpoint。
```

缺点：

```text
需要流式拓扑和 barrier 通道。
对单副本组状态引擎来说复杂度过高。
```

对 BitArk 的判断：

```text
不选择。
架构层级不匹配。
```

### 方案八：Raft Snapshot 标准思想

代表：

```text
etcd
TiKV
Kafka KRaft
RocketMQ DLedger
```

核心思想：

```text
应用层维护 lastAppliedLsn / applyIndex
触发 snapshot 时记录 snapshotLsn
生成该 snapshotLsn 对应的状态机快照
快照完成后允许截断 snapshotLsn 之前的日志
```

实现方式可以不同：

```text
C/C++ 系统可以用 fork + COW。
Java 系统更适合 active/frozen 切换。
底层使用 RocksDB 时可以用 RocksDB 原生 snapshot。
```

对 BitArk 的判断：

```text
BitArk 采用 Raft Snapshot 的语义要求，
但用 Active/Frozen 作为 Java 中的实现路径。
```

## 4. 最终选择

BitArk 当前选择：

```text
方案三：Active + Frozen 双层状态。
```

原因：

```text
1. BitArk 是 Java 项目，不适合 fork + COW。
2. Stop-the-world 会阻塞写入，不符合工业级目标。
3. 完整 MVCC 对当前已读状态过度设计。
4. BitArk 的 bitmap 状态天然可合并，适合 active/frozen。
5. 该方案和 RocksDB/HBase/Cassandra 的核心思想一致，工程上可验证。
```

需要强调：

```text
BitArk 不是要照搬完整 RocksDB。
BitArk 只借鉴 RocksDB 的 Mutable/Immutable MemTable 思想。
```

当前阶段不引入：

```text
SSTable
Compaction
Bloom Filter
Block Cache
完整 MVCC
SQL/事务层
```

## 5. 设计不变量

后续代码必须满足以下不变量：

```text
I1: WAL 先于内存状态更新。
    任何成功写入必须先 append WAL，再 markRead。

I2: WAL append + state apply 对 freezeForSnapshot 表现为原子。
    Full Sync 不能看到“WAL 已写但状态未应用”的半状态。

I3: frozenStatus 只读。
    freeze 之后，写路径不能再修改 frozenStatus。

I4: snapshotBytes 与 snapshotLsn 对齐。
    snapshotBytes 必须包含所有 LSN <= snapshotLsn 的状态变更。

I5: snapshotLsn 之后的写入不能污染 snapshotBytes。
    freeze 后的新写入必须进入新的 activeStatus。

I6: 读路径必须覆盖所有有效状态层。
    isRead 不能只查 activeStatus，必须同时查 frozenStatus。

I7: frozenStatus 生命周期必须明确。
    dump 完成后必须 merge 或 release，不能无限堆积。
```

## 6. 当前版本实现边界

第一版只做单 active + 单 frozen：

```text
activeStatus: 当前写入层
frozenStatus: 当前 Full Sync 正在 dump 的冻结层
snapshotInProgress: 是否已有 Full Sync 正在进行
```

第一版限制：

```text
同一时间只允许一个 Full Sync。
如果已有 frozenStatus 正在 dump，新的 Full Sync 请求应该失败或等待。
```

读写规则：

```text
markRead:
  只写 activeStatus

isRead:
  先查 activeStatus
  再查 frozenStatus

freezeForSnapshot:
  在短临界区内执行指针切换
  frozenStatus = activeStatus
  activeStatus = new empty state
  snapshotLsn = 当前已应用 WAL 进度

dump:
  在锁外序列化 frozenStatus

merge/release:
  dump 完成后处理 frozenStatus 生命周期
```

## 7. 后续演进路线

v1：

```text
单 active + 单 frozen
同一时间只允许一个 Full Sync
dump 完成后 merge/release frozen
```

v2：

```text
frozen 引用计数
允许慢 dump 与读路径安全共存
```

v3：

```text
List<FrozenVersion>
支持多个 Slave 并发 Full Sync
```

v4：

```text
增量 snapshot / chunked snapshot
解决超大状态一次性 byte[] 传输问题
```

v5：

```text
抽象 StorageEngine
支持 InMemoryBitmapEngine 和 RocksDBBitmapEngine
```

## 8. 当前下一步

下一步改造从 ReadStatusEngine 开始。

目标：

```text
把单层 readStatus 改造成 activeStatus + frozenStatus。
```

先设计字段和方法：

```text
字段：
  activeStatus
  frozenStatus
  snapshotInProgress
  lock

方法：
  markRead
  isRead
  freezeForSnapshot
  dumpFrozenSnapshot
  mergeFrozen
```

当前阶段先不做：

```text
多 frozen 链
引用计数
chunked 传输
RocksDB 后端
完整 MVCC
```

这份文档作为后续 Full Sync 多版本快照改造的设计依据。
