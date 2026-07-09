package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "sentinelhub.remote.rtc")
public record RemoteRtcProperties(
        List<String> stunServers,
        String turnUrl,
        String turnUsername,
        String turnCredential
) {
    public List<String> resolvedStunServers() {
        if (stunServers != null && !stunServers.isEmpty()) {
            return stunServers;
        }
        return List.of("stun:stun.l.google.com:19302");
    }
}
