# BitArk LeaderLsn 语义重构设计决策

日期：2026-05-02

## 1. 背景：为什么重新讨论 LSN

BitArk 目前已经具备：

```text
WAL
Snapshot / Checkpoint
Pull Replication
ISR
Full Sync 设计
Active/Frozen 多版本快照方向
```

在推进 Full Sync 和多版本快照时，出现了一个核心架构信号：

```text
系统里出现了多个“看起来都像 LSN”的概念。
```

当前容易混淆的概念包括：

```text
walEngine.append() 返回的位置
FetchRequest.fromLsn
FetchResponse.nextLsn
ReplicationProgressStore 保存的 progress
ReadStatusEngine.lastAppliedLsn
FullSyncResponse.snapshotLsn
```

这些值表面上都叫 LSN，但语义并不完全一致：

```text
有的是本机 WAL 物理写入位置。
有的是 Master/Leader 的复制进度。
有的是状态机已经 apply 到的位置。
有的是快照覆盖到的位置。
```

如果不收敛这套语义，后续 Full Sync、Epoch、Controller、Truncate 都会继续混乱。

## 2. 核心分水岭：Log 是最终数据，还是状态机输入

工业系统里，日志模型大致分两类。

### 路线甲：Log 就是数据本身

代表：

```text
Kafka 普通 topic log
RocketMQ CommitLog
```

特点：

```text
客户端生产消息。
消息追加到 Log。
消费者直接从 Log 读取消息。
系统的主数据形态就是日志。
```

这类系统的坐标设计通常更偏向：

```text
如何高效定位日志。
如何高效顺序写。
如何高效从某个 offset 拉取。
```

### 路线乙：Log 是状态机的输入

代表：

```text
Raft 状态机
etcd
TiKV apply 层
MySQL redo/binlog 与状态页
BitArk
```

特点：

```text
客户端提交的是 command。
command 先写入 Log。
然后 apply 到状态机。
查询读的是状态机，而不是直接读 Log。
```

这类系统的坐标设计必须服务于：

```text
Log 和状态机 apply 进度对齐。
Snapshot 和 Log 进度对齐。
主从切换后日志分叉可截断。
Full Sync 后从正确位置继续增量。
```

BitArk 有 `ReadStatusEngine`，有 `Snapshot`，有 `Full Sync`，所以它属于路线乙。

## 3. 三类工业系统的坐标模型

### 3.1 RocketMQ：物理字节 offset 取向

RocketMQ 的 CommitLog 使用全局物理 offset。

优点：

```text
非常适合顺序写。
MappedFile + PageCache + commitLog byte stream 复制路径直接。
定位和传输天然贴近磁盘布局。
```

代价：

```text
坐标和物理文件布局关系很强。
entry 格式、padding、mapped file 大小、文件边界都会影响实现。
如果把物理 offset 暴露到状态机和 Full Sync 层，会让 snapshot/truncate 与文件布局强耦合。
```

需要修正一点：

```text
RocketMQ 的 absolute physicalOffset 不会因为删除旧文件而“意义改变”。
只要系统保存了 mapped file base offset，absolute offset 仍然稳定。
```

但对 BitArk 来说，问题不在于 RocketMQ 做不到，而在于：

```text
BitArk 后面要做状态机 snapshot、epoch、truncate。
如果复制坐标直接绑定物理字节位置，会让上层持续关心底层文件布局。
```

结论：

```text
BitArk 可以学习 RocketMQ 的 WAL 物理实现和顺序写思想，
但不应该把物理 byte position 作为状态机和复制层的主语。
```

### 3.2 Kafka：逻辑 offset + Pull 复制

Kafka 使用 per-partition offset。

优点：

```text
Follower 通过 FetchRequest(fromOffset) 拉取 records。
Leader 分配 offset，Follower 原样保存。
LeaderEpoch 用于处理 leader 切换后的日志分叉。
```

Kafka 值得 BitArk 学习的是：

```text
Pull 模型。
FetchRequest / FetchResponse。
Follower 自己控制拉取进度。
offset 作为复制协议主坐标。
```

但 Kafka 普通 topic log 的主模型是消息日志：

```text
消费者直接消费 log。
Broker 普通数据主链路不是“内存状态机 snapshot + apply”模型。
```

需要修正一点：

```text
Kafka KRaft metadata log 是状态机模型，也有 snapshot offset。
因此 Kafka 不是完全没有状态机快照，只是普通 topic 数据复制主链路不是 BitArk 当前这种内存状态机 Full Sync。
```

结论：

```text
BitArk 的 Pull 复制可以学 Kafka。
但 BitArk 的状态机 apply / snapshot 语义更适合学 Raft。
```

### 3.3 Raft：逻辑 index + term

Raft 日志条目具有：

```text
index
term
command
```

状态机维护：

```text
commitIndex
lastApplied
snapshotIndex
snapshotTerm
```

这个模型天然服务于复制状态机：

```text
index 表示日志逻辑位置。
term 表示任期 / epoch。
lastApplied 表示状态机已经 apply 到哪里。
snapshotIndex 表示快照覆盖到哪里。
```

这些概念与 BitArk 后续目标直接对应：

```text
leaderLsn            -> Raft index
epoch                -> Raft term
lastAppliedLeaderLsn -> lastApplied
snapshotLeaderLsn    -> snapshotIndex
```

结论：

```text
BitArk 应该采用 Raft 风格的日志语义模型。
当前只采用 index/term/applied/snapshot 这套语义，
不代表现在立刻实现 Raft 选举和多数派提交。
```

## 4. 当前两条路线

### 路线 B：本地坐标与复制坐标分离

当前 BitArk 接近这个路线：

```text
Master 有自己的 globalLsn。
Slave 本地 walEngine.append() 又生成本地 globalLsn。
ReplicationProgressStore 保存 Master nextLsn。
```

优点：

```text
短期改动小。
本地 WAL writer 可以继续自己分配位置。
Slave 本地物理布局自由。
```

缺点：

```text
长期维护两套坐标。
Full Sync 要反复区分 snapshotLsn 是本地坐标还是 leader 坐标。
Epoch / truncate 时必须按 leaderLsn 反查本地物理位置。
切主后日志分叉处理复杂。
Crash recovery 后需要保证本地状态和 ReplicationProgressStore 原子一致。
```

这个路线即使改名为：

```text
localWalLsn
leaderLsn
lastAppliedLeaderLsn
```

也只是解决“读代码不混”，没有解决“两套坐标”的结构成本。

### 路线 A：Leader 分配唯一复制坐标

目标：

```text
全系统复制语义只认 leaderLsn。
```

规则：

```text
Master/Leader 分配 leaderLsn。
Slave/Follower 不分配 leaderLsn。
Slave 原样保存、应用、上报 leaderLsn。
本地物理位置只留在 WalEngine 内部。
```

Master 写入：

```text
leaderLsn = allocator.next()
entry.leaderLsn = leaderLsn
walEngine.append(entry)
engine.markRead(userId, msgId, leaderLsn)
```

Slave Pull 应用：

```text
收到 entry.leaderLsn
walEngine.append(entry)
engine.markRead(userId, msgId, entry.leaderLsn)
progressStore.save(nextLeaderLsn)
```

Full Sync：

```text
FullSnapshot(snapshotBytes, snapshotLeaderLsn)
Slave apply snapshot 后 progress = snapshotLeaderLsn
Slave 从 snapshotLeaderLsn 继续 Pull
```

## 5. 最终决策

BitArk 选择路线 A：

```text
Raft 风格 leaderLsn + epoch 语义模型。
```

原因：

```text
1. BitArk 是状态机模型，不是纯 MQ 日志模型。
2. Full Sync 需要 snapshotBytes 与 snapshotLeaderLsn 精确对齐。
3. Active/Frozen 快照需要状态机记录 lastAppliedLeaderLsn。
4. 后续 Epoch / Controller / Truncate 都需要唯一复制坐标。
5. 本地物理位置应该封装在 WalEngine 内部，不应该泄露到复制层。
```

需要强调：

```text
我们不是现在实现完整 Raft。
我们只是采用 Raft 的日志语义：
  leaderLsn / epoch / lastAppliedLeaderLsn / snapshotLeaderLsn。
```

## 6. 新的命名规范

后续统一使用：

```text
leaderLsn
  Leader 分配的日志位置。
  用于复制、Fetch、ACK、ISR、Full Sync、Epoch、Truncate。

epoch
  Leader 任期。
  后续用于防旧主、日志分叉和 truncate。

lastAppliedLeaderLsn
  当前状态机已经 apply 到的 leaderLsn。

snapshotLeaderLsn
  某份 snapshot 覆盖到的 leaderLsn。

nextLeaderLsn
  下一次 Pull 应该从哪里开始。

WalCheckpoint
  本地 WAL 文件物理坐标。
  只属于 WalEngine / Recovery / 本地 GC。
```

不再建议在复制层使用：

```text
globalLsn
localWalLsn
```

其中：

```text
globalLsn 太模糊。
localWalLsn 容易把本地物理位置提升成业务语义。
```

## 7. 需要改造的核心点

### 7.1 LogEntry 格式

当前需要从业务 entry 扩展为复制日志 entry：

```text
leaderLsn
epoch
type
userId
msgId
crc
```

第一版可以先固定长度，后续再考虑 bodyLen / 可变长 entry。

### 7.2 Master 写入

Master 不再依赖 WalWriter 返回复制 LSN。

Master 应先分配：

```text
leaderLsn
```

然后写入带 leaderLsn 的 entry。

### 7.3 Slave 应用

Slave 不再用本地 walEngine.append 返回值推进状态机进度。

Slave 应使用：

```text
entry.leaderLsn
```

推进：

```text
ReadStatusEngine.lastAppliedLeaderLsn
ReplicationProgressStore.nextLeaderLsn
```

### 7.4 Fetch DTO

后续改名：

```text
FetchRequest.fromLsn  -> fromLeaderLsn
FetchResponse.nextLsn -> nextLeaderLsn
FetchEntryDTO         -> 携带 leaderLsn / epoch
```

### 7.5 Full Sync DTO

后续改名：

```text
FullSyncResponse.snapshotLsn -> snapshotLeaderLsn
```

后续增加：

```text
snapshotEpoch
```

### 7.6 WalEngine 边界

WalEngine 仍然需要本地物理坐标：

```text
segmentIndex
segmentOffset
```

但这些概念只用于：

```text
文件定位
replay
checkpoint
local GC
```

不应该泄露到复制 DTO 和状态机快照语义。

## 8. Trade-off

选择路线 A 的代价：

```text
需要修改 LogEntry 二进制格式。
需要修改 WalWriter append 语义。
需要修改 WalReader decode/replay 逻辑。
需要修改 Fetch DTO 和 Slave apply 逻辑。
短期改动大。
```

选择路线 A 的收益：

```text
全系统只有一套复制坐标。
Full Sync 语义清晰。
Epoch / Controller / Truncate 后续路径清晰。
可以直接对照 Raft / Kafka KRaft 的设计文档学习。
本地物理位置被封装在 WalEngine 内部。
```

最终判断：

```text
BitArk 当前仍处于开发阶段，没有历史数据兼容和灰度升级压力。
因此应该承担一次性重构成本，切到路线 A。
```

## 9. 下一步改造顺序

建议顺序：

```text
1. 设计新的 LogEntry 二进制格式。
2. 给 LogEntry 增加 leaderLsn / epoch。
3. 修改 WalWriter：append 不再生成复制 LSN，而是落盘 entry 自带 leaderLsn。
4. 修改 WalReader：decode/replay 返回 entry.leaderLsn。
5. 修改 Master 写入：由 Master 分配 leaderLsn。
6. 修改 FetchEntryDTO：携带 leaderLsn / epoch。
7. 修改 Slave apply：使用 entry.leaderLsn 更新状态机。
8. 修改 ProgressStore / DTO 命名：nextLeaderLsn / snapshotLeaderLsn。
9. 回到 Active/Frozen Full Sync。
```

这份文档作为 BitArk LSN 语义重构的设计依据。
