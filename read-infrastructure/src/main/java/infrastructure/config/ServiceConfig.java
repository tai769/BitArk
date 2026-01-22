package infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bitark.service.ReadServiceImpl;
import com.bitark.wal.WalEngine;

@Configuration
public class ServiceConfig {

    @Bean
    public ReadServiceImpl readService(WalEngine walEngine) throws Exception {
        return new ReadServiceImpl(walEngine);
    }
}
