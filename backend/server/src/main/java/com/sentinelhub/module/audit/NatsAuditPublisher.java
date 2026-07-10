package com.sentinelhub.module.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.AuditNatsProperties;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
public class NatsAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsAuditPublisher.class);

    private final AuditNatsProperties properties;
    private final ObjectMapper objectMapper;
    private volatile Connection connection;
    private volatile JetStream jetStream;

    public NatsAuditPublisher(AuditNatsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public void publish(String tenantId, String actorType, String actorId, String action,
                        String resource, String resourceId, String detailJson, String ip) {
        if (!properties.enabled()) {
            return;
        }
        try {
            ensureConnected();
            AuditEventMessage message = new AuditEventMessage(
                    UUID.randomUUID().toString(),
                    tenantId,
                    actorType,
                    actorId,
                    action,
                    resource,
                    resourceId,
                    detailJson,
                    ip);
            byte[] payload = objectMapper.writeValueAsBytes(message);
            jetStream.publish(properties.subject(), payload);
        } catch (Exception e) {
            log.warn("NATS audit publish failed, falling back is handled by caller: {}", e.getMessage());
            throw new IllegalStateException("nats publish failed", e);
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
        jetStream = connection.jetStream();
    }
}
