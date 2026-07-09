package com.sentinelhub;

import com.sentinelhub.config.AiLlmProperties;
import com.sentinelhub.config.JwtProperties;
import com.sentinelhub.config.MinioProperties;
import com.sentinelhub.config.RemoteRtcProperties;
import com.sentinelhub.config.SeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.sentinelhub")
@EnableConfigurationProperties({JwtProperties.class, SeedProperties.class, MinioProperties.class, AiLlmProperties.class, RemoteRtcProperties.class})
public class SentinelHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelHubApplication.class, args);
    }
}
