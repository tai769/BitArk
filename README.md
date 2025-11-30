# Read Service

一个纯内存、高性能、分布式的已读服务。
旨在解决海量用户（亿级）与海量消息（百亿级）状态下的"已读/未读"判断问题，要求微秒级响应与数据强一致性。

## 核心设计哲学

1. **计算与IO分离**：网络IO交给成熟的框架处理，核心计算由自研引擎完成
2. **无锁化设计 (Lock-Free)**：通过数据分片 + 线程封闭（Thread Confinement），消除并发锁竞争
3. **日志结构化 (Log-Structured)**：以内存计算为主，磁盘仅作为顺序写的日志（WAL），保证极致吞吐

## 特性

1. **高性能**：基于Spring Boot的异步网络框架，支持高并发请求处理
2. **纯内存操作**：所有数据存储在内存中，提供微秒级响应
3. **横向扩展**：支持多节点集群部署，可通过增加节点提升处理能力
4. **高可用**：通过集群机制避免单点故障，确保服务稳定运行
5. **自研集群协议**：自主实现节点间通信和数据同步机制
6. **WAL持久化**：通过顺序写日志保证数据持久性和强一致性

## 技术栈

- Java 11+
- Netty 4.1.77.Final（网络通信）
- Maven（项目构建）

## 项目结构

```
src/main/java/com/example/readservice/
├── adapter/                       // 接口适配层
│   └── ApiController.java         // Spring Boot控制器
├── cluster/                       // 集群协调层
│   ├── SlotRouter.java            // 槽位路由器
│   └── ReplicationManager.java    // 复制状态机
├── engine/                        // 存储内核层
│   ├── PartitionScheduler.java    // 分区调度器
│   └── MemoryBitmap.java          // 内存位图(RoaringBitmap)
├── infrastructure/                // 基础设施层
│   └── WriteAheadLog.java         // WAL日志与组提交
└── common/                        // 公共模块
    └── Constants.java             // 公共常量定义
```

## 快速开始

1. 编译项目：
   ```
   mvn clean package
   ```

2. 运行服务：
   ```
   java -jar target/read-service-1.0.0.jar
   ```

3. 访问服务：
   ```
   curl http://localhost:8080/
   ```

## 逻辑架构分层

系统自上而下分为四层，层级边界严密，下层对上层透明：

1. **接口适配层 (Interface Adapter)**：系统的"大门"，负责协议转换
2. **集群协调层 (Cluster Coordination)**：系统的"大脑"，解决分布式的分片与高可用
3. **存储内核层 (Storage Kernel)**：系统的"心脏"，解决单机的极致性能与持久化
4. **基础设施层 (Infrastructure)**：文件存储，包括WAL日志和内存快照

## 后续工作

1. 实现各层核心组件功能
2. 开发完整的RESTful API接口
3. 实现集群节点自动发现与路由算法
4. 实现WAL日志与组提交机制
5. 实现主备复制与数据同步
6. 性能压测和调优