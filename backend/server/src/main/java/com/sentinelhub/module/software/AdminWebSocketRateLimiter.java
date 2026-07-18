package com.sentinelhub.module.software;

import com.sentinelhub.config.WebSocketLimitsProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AdminWebSocketRateLimiter {

    private final WebSocketLimitsProperties limits;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AdminWebSocketRateLimiter(WebSocketLimitsProperties limits) {
        this.limits = limits;
    }

    public boolean allowBroadcast(String tenantId) {
        int max = limits.maxEventsPerSecondPerTenant();
        if (max <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Window window = windows.compute(tenantId, (key, existing) -> {
            if (existing == null || now - existing.epochMs >= 1000L) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return window.count.get() <= max;
    }

    private static final class Window {
        private final long epochMs;
        private final AtomicInteger count;

        private Window(long epochMs, AtomicInteger count) {
            this.epochMs = epochMs;
            this.count = count;
        }
    }
}
