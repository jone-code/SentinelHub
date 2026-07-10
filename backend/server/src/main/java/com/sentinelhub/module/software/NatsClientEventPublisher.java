package com.sentinelhub.module.software;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.ClientEventNatsProperties;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class NatsClientEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsClientEventPublisher.class);

    private final ClientEventNatsProperties properties;
    private final ObjectMapper objectMapper;
    private volatile Connection connection;
    private volatile JetStream jetStream;

    public NatsClientEventPublisher(ClientEventNatsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public void publish(String tenantId, String deviceId, String clientId, String eventType,
                        String severity, String detailJson) {
        if (!properties.enabled()) {
            return;
        }
        try {
            ensureConnected();
            ClientEventMessage message = new ClientEventMessage(
                    UUID.randomUUID().toString(),
                    tenantId,
                    deviceId,
                    clientId,
                    eventType,
                    severity,
                    detailJson);
            byte[] payload = objectMapper.writeValueAsBytes(message);
            jetStream.publish(properties.subject(), payload);
        } catch (Exception e) {
            log.warn("NATS client event publish failed: {}", e.getMessage());
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
