package com.bitark.engine.service;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import com.bitark.engine.ReadStatusEngine;
import com.bitark.engine.config.RecoveryConfig;
import com.bitark.engine.wal.WalEngine;
import com.bitark.engine.config.ReplicationConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;
import com.bitark.engine.recover.SnapshotManager;
import com.bitark.engine.checkpoint.CheckpointManager;
import com.bitark.commons.dto.ReplicationAck;
import com.bitark.commons.dto.ReplicationRequest;
import com.bitark.commons.log.LogEntry;
import com.bitark.commons.wal.WalCheckpoint;




@Slf4j
public class ReadServiceImpl implements ReadService {
    

    private final ConcurrentHashMap<String, WalCheckpoint> slaveAckMap = new ConcurrentHashMap<>();

    private final RecoveryConfig recoveryConfig;


    ReadStatusEngine engine = new ReadStatusEngine();

    private CheckpointManager checkpointManager;

    private final RestTemplate restTemplate;

    private final ReplicationConfig replicationConfig;

    private final ExecutorService executorService;

    private final WalEngine walEngine;
    private SnapshotManager snapshotManager;

    public ReadServiceImpl(WalEngine walEngine, RecoveryConfig recoveryConfig, RestTemplate restTemplate, ReplicationConfig replicationConfig, ExecutorService executorService) throws Exception {
        this.recoveryConfig = recoveryConfig;
        this.restTemplate = restTemplate;
        this.replicationConfig = replicationConfig;
        this.executorService = executorService;
        this.walEngine = walEngine;
        this.snapshotManager = new SnapshotManager(Paths.get(recoveryConfig.getSnapshotPath()));
        this.checkpointManager = new CheckpointManager(Paths.get(recoveryConfig.getCheckpointPath()));
    }

    // 只负责 wal的操作
    @Override
    public void read(Long userId, Long msgId) throws Exception {
        try {
            LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId, msgId);
            WalCheckpoint lsn = walEngine.append(entry);;
            engine.markRead(userId, msgId);

            /*
             * 发送同步请求
             */

            executorService.submit(() -> {
                
                try {

                    //1.  构建Json请求 
                    ReplicationRequest request = new ReplicationRequest();
                    request.setUserId(userId);
                    request.setMsgId(msgId);
                    request.setSegmentIndex(lsn.getSegmentIndex());
                    request.setOffset(lsn.getSegmentOffset());

                    //2. 发送Json请求(注意Url变了, 不再带参数)
                    String url = replicationConfig.getSlaveUrl();

                    //3. 接受回执
                    ReplicationAck ack = restTemplate.postForObject(url, request, ReplicationAck.class);;

                    //4. 登记账本(记录这个Slave的最新进度)
                    if (ack != null) {
                        WalCheckpoint slaveCheckpoint = ack.toCheckpoint();
                        slaveAckMap.put(url, slaveCheckpoint);
                        log.info("✅ Slave ACK: segmentIndex={}, offset={}", 
                                slaveCheckpoint.getSegmentIndex(), slaveCheckpoint.getSegmentOffset());
                    }
                } catch (Exception e) {
                    log.error("sync error", e);
                }

            });

        } catch (Exception e) {
            walEngine.close();
            log.error("read error", e);
        }
    }

    @Override
    @PostConstruct
    public void recover() throws Exception {
        log.info("开始恢复内存状态...");
        Path snapshotPath = Paths.get(recoveryConfig.getSnapshotPath());
        WalCheckpoint cp = null;

        // 1. 尝试 snapshot 恢复（失败也不中断）
        try {
            if (Files.exists(snapshotPath)) {
                snapshotManager.load(engine);
                log.info("✅ Snapshot 恢复成功");
            } else {
                log.warn("Snapshot 文件不存在，将依赖 WAL 恢复");
            }
        } catch (Exception e) {
            log.error("Snapshot 读取失败，将依赖 WAL 恢复: {}", e.getMessage());
        }

        // 2. 尝试读取 checkpoint（失败就退化成全量 replay）
        try {
            cp = checkpointManager.load();
        } catch (NoSuchFileException | FileNotFoundException e) {
            log.warn("Checkpoint 文件不存在，将使用全量 WAL 回放");
        } catch (Exception e) {
            log.error("Checkpoint 读取失败，将使用全量 WAL 回放", e);
        }

        // 3. 根据 cp 是否存在，决定用全量还是增量 replay
        if (cp == null) {
            walEngine.replay(entry -> engine.markRead(entry.getUserId(), entry.getMsgId()));
        } else {
            walEngine.replayFrom(cp, entry -> engine.markRead(entry.getUserId(), entry.getMsgId()));
        }

        log.info("✅ Recovery Complete. Engine instance ID: {}", System.identityHashCode(engine));
    }

    @Override
    public boolean isRead(Long userId, Long msgId) {

        return engine.isRead(userId, msgId);
    }

    @Override
    public void readFromMaster (Long userId, Long msgId) throws Exception {
        log.info("readFromMaster");
        LogEntry entry = new LogEntry(LogEntry.READ_ENTRY, userId, msgId);
        walEngine.append(entry);
        engine.markRead(userId, msgId);
    }

    @Override
    public void snapshot() throws Exception {
        log.info("开始保存 snapshot...");
        snapshotManager.save(engine);
        log.info("✅ Snapshot 已保存到: {}", recoveryConfig.getSnapshotPath());

        WalCheckpoint masterCheckpoint = walEngine.currCheckpoint();
        
        log.info("Current checkpoint: {}", masterCheckpoint);
        checkpointManager.save(masterCheckpoint);


        WalCheckpoint minSlaveCheckpoint = getMinSlaveAckLSN();

        WalCheckpoint safeCheckpoint;
        log.info("Min slave checkpoint: {}", minSlaveCheckpoint);
        if (minSlaveCheckpoint == null) {
            // 没有slave,用master自己的进度
            safeCheckpoint = masterCheckpoint;
            log.info("No slaves, using master checkpoint: {}", safeCheckpoint);
           
        }else{
            // 有slave 必须要等最慢的slave
            safeCheckpoint = minSlaveCheckpoint.compareTo(masterCheckpoint) < 0 ? minSlaveCheckpoint : masterCheckpoint;
            log.info("🧹 GC Safe Point (slowest slave): {}", safeCheckpoint);
        }
        walEngine.gcOldSegment(safeCheckpoint);
        log.info("✅ Old segments have been cleaned up");
            
    }

    /*
    * 获取slave中进度最慢的那个lsn ,用于决定WAL GC的安全水位线
    */
    private WalCheckpoint getMinSlaveAckLSN(){
        if (slaveAckMap.isEmpty()) {
            return null;
        }
        WalCheckpoint minCheckpoint = null;
        for(WalCheckpoint cp : slaveAckMap.values()){
            if (minCheckpoint == null || cp.compareTo(minCheckpoint) < 0) {
                minCheckpoint = cp;
            }
        }
        return minCheckpoint;
        
    }





}
