package com.sentinelhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class InstanceIdConfig {

    @Bean
    public String sentinelInstanceId(
            @Value("${sentinelhub.instance-id:}") String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return UUID.randomUUID().toString();
    }
}
