package adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.bitark.service.ReadServiceImpl;

@Configuration
public class ServiceConfig {

    @Bean
    public ReadServiceImpl readService() {
        return new ReadServiceImpl();
    }
}