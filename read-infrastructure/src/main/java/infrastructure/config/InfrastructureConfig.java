package infrastructure.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import infrastructure.thread.ThreadUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class InfrastructureConfig {

    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService syncExecutor() {
        return ThreadUtils.newThreadPoolExecutor(
            32, 64, 1000L, TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<>(),
            "sync-pool",
                true);
    }

}
