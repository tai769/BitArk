package com.bitark.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bitark.engine.WalEngine;

@Configuration
public class ServiceConfig {

    @Bean
    public ReadServiceImpl readService(WalEngine walEngine) throws Exception {
        return new ReadServiceImpl(walEngine);
    }
}