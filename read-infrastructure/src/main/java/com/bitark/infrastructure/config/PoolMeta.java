package com.bitark.infrastructure.config;

import lombok.Getter;

import java.util.concurrent.ExecutorService;

@Getter
public class PoolMeta {
    private final ExecutorService executor;

    private final String domain;

    private final String name;

    private final long shutdownTimeout;

    public PoolMeta(ExecutorService executor, String domain, String name, long shutdownTimeout) {
        this.executor = executor;
        this.domain = domain;
        this.name = name;
        this.shutdownTimeout = shutdownTimeout;
    }

    @Override
    public String toString() {
        return String.format("[%s-%s]", domain, name);
    }
}
