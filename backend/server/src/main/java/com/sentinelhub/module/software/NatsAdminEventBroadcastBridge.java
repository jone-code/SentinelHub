package com.sentinelhub.module.software;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.WebSocketBroadcastProperties;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NatsAdminEventBroadcastBridge implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NatsAdminEventBroadcastBridge.class);

    private final WebSocketBroadcastProperties properties;
    private final ObjectMapper objectMapper;
    private final AdminWebSocketSessionRegistry sessionRegistry;
    private volatile Connection connection;

    public NatsAdminEventBroadcastBridge(WebSocketBroadcastProperties properties,
                                         ObjectMapper objectMapper,
                                         AdminWebSocketSessionRegistry sessionRegistry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        Thread starter = new Thread(this::connectAndSubscribe, "nats-ws-broadcast");
        starter.setDaemon(true);
        starter.start();
    }

    private void connectAndSubscribe() {
        try {
            Options options = new Options.Builder()
                    .server(properties.url())
                    .connectionTimeout(Duration.ofSeconds(5))
                    .build();
            connection = Nats.connect(options);
            Dispatcher dispatcher = connection.createDispatcher(msg -> {
                try {
                    String json = new String(msg.getData(), StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> envelope = objectMapper.readValue(json, Map.class);
                    String tenantId = String.valueOf(envelope.get("tenant_id"));
                    Object payload = envelope.get("payload");
                    if (tenantId.isBlank() || payload == null) {
                        return;
                    }
                    sessionRegistry.broadcast(tenantId, objectMapper.writeValueAsString(payload));
                } catch (Exception e) {
                    log.warn("NATS WS broadcast message handling failed: {}", e.getMessage());
                }
            });
            dispatcher.subscribe(properties.subject());
            log.info("NATS multi-instance WebSocket broadcast subscribed (subject={})", properties.subject());
        } catch (Exception e) {
            log.error("NATS WS broadcast subscriber failed: {}", e.getMessage());
        }
    }

    public void publish(String tenantId, Map<String, Object> payload) {
        if (!properties.enabled()) {
            return;
        }
        try {
            ensureConnected();
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("tenant_id", tenantId);
            envelope.put("payload", payload);
            connection.publish(properties.subject(), objectMapper.writeValueAsBytes(envelope));
        } catch (Exception e) {
            log.warn("NATS WS broadcast publish failed: {}", e.getMessage());
        }
    }

    private synchronized void ensureConnected() throws Exception {
        if (connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
            return;
        }
        Options options = new Options.Builder()
                .server(properties.url())
                .connectionTimeout(Duration.ofSeconds(3))
                .build();
        connection = Nats.connect(options);
    }

    @PreDestroy
    void shutdown() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }
}
