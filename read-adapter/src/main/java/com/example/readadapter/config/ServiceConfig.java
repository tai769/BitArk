package com.example.readadapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import service.ReadServiceImpl;

@Configuration
public class ServiceConfig {

    @Bean
    public ReadServiceImpl readService() {
        return new ReadServiceImpl();
    }
}