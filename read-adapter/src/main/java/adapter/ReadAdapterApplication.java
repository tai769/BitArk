package adapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"adapter", "com.bitark"})
public class ReadAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadAdapterApplication.class, args);
    }

}