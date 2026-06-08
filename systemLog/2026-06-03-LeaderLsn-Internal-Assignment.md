# LeaderLsn 内部分配：业务层不参与 LSN 分配

日期：2026-06-03

## 1. 现状问题

当前写入链路：

```java
// 业务层需要自己填 leaderLsn 占位符
WalRecord record = new WalRecord(0, 1, CommandTypes.READ_MARK, payload);
Long leaderLsn = walEngine.append(record);
engine.markRead(userId, msgId, leaderLsn);
```

问题：

```text
1. 业务层被迫知道 leaderLsn 的存在，但又不能分配它（只能填 0 占位）
2. WalRecord 对象里的 leaderLsn = 0，和磁盘上实际写入的 leaderLsn 不一致
3. 业务层必须用 append 的返回值来获取真正的 leaderLsn，容易出错
4. 如果未来有多个业务命令（READ_MARK、DELETE_MARK、CONFIG_CHANGE），
   每个都要重复这套"填 0 → append → 拿返回值"的模式
```

本质矛盾：

```text
leaderLsn 是 WAL 存储层的坐标，不是业务层的概念。
但当前 WalRecord 的构造器要求业务层传入 leaderLsn，
导致存储层的细节泄露到了业务层。
```

## 2. 目标状态

```java
// 业务层完全不关心 leaderLsn
WalRecord record = WalRecord.create(CommandTypes.READ_MARK, payload);
walEngine.append(record);

// leaderLsn 由 WalWriter 内部分配、内部分配后自动回填或通过结果返回
```

业务层只需要关心：

```text
1. 命令类型（type）
2. 命令内容（payload）
3. 写入成功后拿到 leaderLsn（用于推进状态机进度）
```

不需要关心：

```text
1. leaderLsn 怎么分配
2. WalRecord 内部字段怎么填
3. WalWriter 内部怎么映射物理位置
```

## 3. 改造方案

### 3.1 WalRecord 增加工厂方法

```java
@Data
public class WalRecord {

    private long leaderLsn;
    private int epoch;
    private short type;
    private byte[] payload;

    /**
     * 业务层创建 WalRecord 的入口。
     *
     * leaderLsn 由 WalWriter 分配，业务层不需要传入。
     * epoch 默认为 1（当前单 Leader 阶段）。
     */
    public static WalRecord create(short type, byte[] payload) {
        return new WalRecord(0, 1, type, payload);
        // leaderLsn = 0 是内部占位，业务层不需要关心
    }
}
```

### 3.2 WalEngine.append 返回 AppendResult

当前 `append` 只返回 `Long leaderLsn`。扩展为返回结构化结果：

```java
/**
 * WAL 写入结果。
 *
 * 包含 WalWriter 分配的逻辑位置和物理位置。
 * 业务层只需要 leaderLsn，物理位置是 WAL 内部的。
 */
@Data
@AllArgsConstructor
public class AppendResult {
    /** WalWriter 分配的全局逻辑日志序号 */
    private final long leaderLsn;

    /** WAL 内部：写入的 segment 文件编号（业务层不关心） */
    private final long segmentIndex;

    /** WAL 内部：写入的 segment 内偏移量（业务层不关心） */
    private final long offset;

    /** WAL 内部：record 的完整字节长度（业务层不关心） */
    private final int recordLength;
}
```

WalEngine 接口改为：

```java
public interface WalEngine {
    AppendResult append(WalRecord record);  // 之前返回 Long
    // ... 其他方法不变
}
```

### 3.3 业务层使用方式

```java
// ReadCommandServiceImpl.read()
@Override
public void read(Long userId, Long msgId) throws Exception {
    // 1. 构造命令（业务层只关心 type 和 payload）
    ReadMarkCommand command = new ReadMarkCommand(userId, msgId);
    byte[] payload = ReadMarkCommandCodec.encode(command);
    WalRecord record = WalRecord.create(CommandTypes.READ_MARK, payload);

    // 2. 写入 WAL，拿到结果
    AppendResult result = walEngine.append(record);

    // 3. 用 leaderLsn 推进状态机
    engine.markRead(userId, msgId, result.getLeaderLsn());
}
```

### 3.4 WalWriter 内部自动回填 leaderLsn

WalWriter 在分配 leaderLsn 后，自动回填到 WalRecord 对象中：

```java
// WalWriter_V2.ioLoop() 内部
for (WriteRequest req : batch) {
    // 1. 分配 leaderLsn（全局单调递增）
    long leaderLsn = nextLeaderLsn.getAndIncrement();

    // 2. 回填到 WalRecord 对象
    req.record.setLeaderLsn(leaderLsn);

    // 3. 编码（此时 leaderLsn 已经是正确的值）
    byte[] encoded = WalRecordCodec.encode(req.record);

    // 4. 记录物理位置
    req.writtenSegmentIndex = this.currentIndex;
    req.writtenOffset = fileChannel.position() + writeBuffer.position();
    req.writtenRecordLength = encoded.length;

    // 5. 写入 buffer
    ensureWritableSpace(encoded.length);
    writeBuffer.put(encoded);
}
```

这样 WalRecord 对象里的 leaderLsn 和磁盘上的一致，不再有"对象里是 0、磁盘上是真正的值"的不一致。

## 4. 改造顺序

```text
第一步：WalRecord 增加 create() 工厂方法（纯新增，不改现有代码）
第二步：定义 AppendResult 类（纯新增）
第三步：WalWriter_V2 内部增加 leaderLsn 分配器（AtomicLong）
第四步：WalWriter_V2.ioLoop() 回填 leaderLsn 到 WalRecord
第五步：WalEngine 接口 append 返回值从 Long 改为 AppendResult
第六步：GroupCommitWalEngine 适配新返回值
第七步：业务层改用 WalRecord.create() + AppendResult.getLeaderLsn()
```

## 5. 与 WalIndex 的关系

AppendResult 同时服务于 WalIndex 的构建：

```java
// GroupCommitWalEngine.append()
@Override
public AppendResult append(WalRecord record) {
    AppendResult result = writer.append(record).join();

    // 用 AppendResult 构建 WalIndex
    walIndex.put(
        result.getLeaderLsn(),
        new WalPosition(result.getSegmentIndex(), result.getOffset(), result.getRecordLength())
    );

    return result;
}
```

这样 leaderLsn 分配、物理位置记录、WalIndex 注册全部在 WalEngine 内部闭环，业务层完全透明。

## 6. 不变量

```text
I1: leaderLsn 由 WalWriter 全局唯一分配，业务层不能指定
I2: WalRecord 对象中的 leaderLsn 在 append 之后与磁盘一致
I3: AppendResult 是 append 的唯一返回值，业务层从中获取 leaderLsn
I4: WalPosition 只在 WalEngine 内部使用，不暴露给业务层
I5: epoch 当前固定为 1，后续由控制面分配
```

## 7. Trade-off

选择内部分配的代价：

```text
需要修改 WalEngine 接口返回值（从 Long → AppendResult）
需要在 WalWriter 内部增加 leaderLsn 分配器
需要修改所有 walEngine.append() 的调用方
```

选择内部分配的收益：

```text
业务层彻底不感知 leaderLsn 的分配过程
WalRecord 对象和磁盘数据一致
WalIndex 构建闭环在 WalEngine 内部
为后续 epoch 由控制面分配预留扩展点
```
