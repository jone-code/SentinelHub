package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "sentinelhub.remote.rtc")
public record RemoteRtcProperties(
        List<String> stunServers,
        String turnUrl,
        List<String> turnUrls,
        String turnUsername,
        String turnCredential,
        String turnSecret,
        Integer turnCredentialTtlSeconds
) {
    private static final String DEFAULT_STUN = "stun:stun.l.google.com:19302";

    public List<String> resolvedStunServers() {
        if (stunServers != null && !stunServers.isEmpty()) {
            return stunServers;
        }
        return List.of(DEFAULT_STUN);
    }

    /**
     * Resolved TURN URL list (supports comma-separated turnUrl fallback).
     */
    public List<String> resolvedTurnUrls() {
        if (turnUrls != null && !turnUrls.isEmpty()) {
            return turnUrls;
        }
        if (turnUrl != null && !turnUrl.isBlank()) {
            if (turnUrl.contains(",")) {
                return Arrays.stream(turnUrl.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
            return List.of(turnUrl.trim());
        }
        return List.of();
    }

    public boolean hasTurn() {
        return !resolvedTurnUrls().isEmpty();
    }

    public boolean usesEphemeralCredentials() {
        return turnSecret != null && !turnSecret.isBlank();
    }

    public int resolvedCredentialTtlSeconds() {
        if (turnCredentialTtlSeconds != null && turnCredentialTtlSeconds > 0) {
            return turnCredentialTtlSeconds;
        }
        return 86400;
    }

    public List<java.util.Map<String, Object>> buildIceServers() {
        List<java.util.Map<String, Object>> iceServers = new ArrayList<>();
        java.util.Map<String, Object> stunEntry = new java.util.LinkedHashMap<>();
        stunEntry.put("urls", resolvedStunServers());
        iceServers.add(stunEntry);

        List<String> urls = resolvedTurnUrls();
        if (urls.isEmpty()) {
            return iceServers;
        }

        java.util.Map<String, Object> turn = new java.util.LinkedHashMap<>();
        turn.put("urls", urls.size() == 1 ? urls.get(0) : urls);

        if (usesEphemeralCredentials()) {
            TurnCredentialGenerator.TurnCredentials creds =
                    TurnCredentialGenerator.generate(turnSecret, resolvedCredentialTtlSeconds());
            turn.put("username", creds.username());
            turn.put("credential", creds.credential());
        } else {
            if (turnUsername != null && !turnUsername.isBlank()) {
                turn.put("username", turnUsername);
            }
            if (turnCredential != null && !turnCredential.isBlank()) {
                turn.put("credential", turnCredential);
            }
        }
        iceServers.add(turn);
        return iceServers;
    }
}
