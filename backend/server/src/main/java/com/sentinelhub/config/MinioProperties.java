package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.minio")
public record MinioProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region
) {}
