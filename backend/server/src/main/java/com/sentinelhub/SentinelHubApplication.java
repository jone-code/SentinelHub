package com.sentinelhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sentinelhub")
public class SentinelHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelHubApplication.class, args);
    }
}
