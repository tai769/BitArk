package com.bitark.engine.WalReader;

import com.bitark.commons.log.WalRecord;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 单个 WAL segment 文件的一次批量读取结果。
 *
 * <p>它只描述“从一个具体文件中读到了哪些 WalRecord，以及下一次从哪里继续读”。
 *
 * <p>注意：FileReadBatch 不负责跨 segment 聚合，也不负责解释 payload 的业务含义。
 */
@Data
@AllArgsConstructor
public class FileReadBatch {

    /**
     * 本次读取到的通用 WAL records。
     */
    private List<WalRecord> records;

    /**
     * 下一次应该从当前 segment 文件的哪个 offset 继续读取。
     */
    private long nextOffset;

    /**
     * 是否已经读到当前 segment 文件末尾。
     */
    private boolean reachFileEnd;
}
