package com.bitark.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.client.RestTemplate;

@Configuration
public class InfrastructureConfig {




    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }

    @Bean
    public ThreadPoolManager threadPoolManager() {
        return new ThreadPoolManager();
    }






}
