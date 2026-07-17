package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.audit.timeline")
public record TimelineProperties(
        boolean fallbackToHot
) {
    public TimelineProperties {
        // default true when unset in yaml via missing key — Spring binds false only if explicitly false
    }
}
