package com.bitark.engine.service.query;

import com.bitark.engine.ReadStatusEngine;

public class ReadQueryServiceImpl implements ReadQueryService{

    private final ReadStatusEngine engine;

    public ReadQueryServiceImpl(ReadStatusEngine engine) {
        this.engine = engine;
    }
    @Override
    public boolean isRead(Long userId, Long msgId) {
        return engine.isRead(userId, msgId);
    }
}
