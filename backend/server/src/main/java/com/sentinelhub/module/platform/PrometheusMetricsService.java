package com.sentinelhub.module.platform;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrometheusMetricsService {

    private final MeterRegistry meterRegistry;

    public PrometheusMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("websocket", Map.of(
                "connections_total", gaugeValue("sentinel.websocket.connections.total"),
                "connections_tenants", gaugeValue("sentinel.websocket.connections.tenants"),
                "broadcasts_throttled", counterValue("sentinel.websocket.broadcasts.throttled"),
                "connections_rejected_global", counterValue("sentinel.websocket.connections.rejected.global")));
        out.put("nats", Map.of(
                "audit_messages_processed", counterValue("sentinel.nats.audit.messages.processed"),
                "audit_batches_failed", counterValue("sentinel.nats.audit.batches.failed"),
                "audit_messages_dlq", counterValue("sentinel.nats.audit.messages.dlq"),
                "client_events_messages_processed", counterValue("sentinel.nats.client_events.messages.processed"),
                "client_events_batches_failed", counterValue("sentinel.nats.client_events.batches.failed"),
                "client_events_messages_dlq", counterValue("sentinel.nats.client_events.messages.dlq")));
        out.put("series_keys", List.of(
                "websocket_connections",
                "nats_audit_messages",
                "nats_client_events_messages",
                "ws_rejected_global"));
        return out;
    }

    public Map<String, Number> chartPoint() {
        Map<String, Number> point = new LinkedHashMap<>();
        point.put("websocket_connections", gaugeValue("sentinel.websocket.connections.total"));
        point.put("nats_audit_messages", counterValue("sentinel.nats.audit.messages.processed"));
        point.put("nats_client_events_messages", counterValue("sentinel.nats.client_events.messages.processed"));
        point.put("ws_rejected_global", counterValue("sentinel.websocket.connections.rejected.global"));
        return point;
    }

    private double gaugeValue(String name) {
        var meter = Search.in(meterRegistry).name(name).gauge();
        return meter != null ? meter.value() : 0.0;
    }

    private double counterValue(String name) {
        var meter = Search.in(meterRegistry).name(name).counter();
        return meter != null ? meter.count() : 0.0;
    }
}
