package com.sentinelhub.config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * TURN REST API time-limited credentials (coturn use-auth-secret).
 * Username: {@code <expiry_unix>:<tag>}, password: Base64(HMAC-SHA1(secret, username)).
 */
public final class TurnCredentialGenerator {

    private static final String TAG = "sentinel";

    private TurnCredentialGenerator() {
    }

    public record TurnCredentials(String username, String credential) {
    }

    public static TurnCredentials generate(String secret, int ttlSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("turn secret required");
        }
        int ttl = ttlSeconds > 0 ? ttlSeconds : 86400;
        long expiry = Instant.now().getEpochSecond() + ttl;
        String username = expiry + ":" + TAG;
        String credential = hmacSha1Base64(secret, username);
        return new TurnCredentials(username, credential);
    }

    static String hmacSha1Base64(String secret, String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(username.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("TURN credential generation failed", e);
        }
    }
}
