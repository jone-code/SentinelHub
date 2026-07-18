package com.sentinelhub;

import com.sentinelhub.config.AuditClickHouseProperties;
import com.sentinelhub.config.AuditNatsProperties;
import com.sentinelhub.config.ClientEventNatsProperties;
import com.sentinelhub.config.TimelineProperties;
import com.sentinelhub.config.TimelineSyncProperties;
import com.sentinelhub.config.WebSocketBroadcastProperties;
import com.sentinelhub.config.WebSocketLimitsProperties;
import com.sentinelhub.config.AiLlmProperties;
import com.sentinelhub.config.JwtProperties;
import com.sentinelhub.config.MinioProperties;
import com.sentinelhub.config.RemoteRtcProperties;
import com.sentinelhub.config.SeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.sentinelhub")
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, SeedProperties.class, MinioProperties.class, AiLlmProperties.class, RemoteRtcProperties.class, AuditClickHouseProperties.class, AuditNatsProperties.class, ClientEventNatsProperties.class, TimelineProperties.class, TimelineSyncProperties.class, WebSocketBroadcastProperties.class, WebSocketLimitsProperties.class})
public class SentinelHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelHubApplication.class, args);
    }
}
