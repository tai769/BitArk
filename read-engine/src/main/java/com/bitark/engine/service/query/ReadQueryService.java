package com.bitark.engine.service.query;

public interface ReadQueryService {
    boolean isRead(Long userId, Long msgId);
}
