package com.sentinelhub.module.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.AuditNatsProperties;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class NatsAuditConsumer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NatsAuditConsumer.class);

    private final AuditNatsProperties properties;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public NatsAuditConsumer(AuditNatsProperties properties,
                             ObjectMapper objectMapper,
                             AuditService auditService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        Thread consumerThread = new Thread(this::runConsumer, "nats-audit-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("NATS async audit consumer started (subject={}, batch={})",
                properties.subject(), properties.batchSize());
    }

    private void runConsumer() {
        try {
            Options options = new Options.Builder()
                    .server(properties.url())
                    .connectionTimeout(Duration.ofSeconds(5))
                    .build();
            Connection nc = Nats.connect(options);
            JetStreamManagement jsm = nc.jetStreamManagement();
            ensureStream(jsm);

            JetStream js = nc.jetStream();
            PullSubscribeOptions pullOpts = PullSubscribeOptions.builder()
                    .durable(properties.durable())
                    .build();
            JetStreamSubscription sub = js.subscribe(properties.subject(), pullOpts);

            while (!Thread.currentThread().isInterrupted()) {
                if (shouldBackoff(jsm)) {
                    Thread.sleep(properties.backlogBackoffMs());
                    continue;
                }
                int batchSize = properties.batchSize();
                sub.pull(batchSize);
                List<Message> messages = sub.fetch(batchSize, Duration.ofMillis(properties.fetchTimeoutMs()));
                if (messages.isEmpty()) {
                    continue;
                }
                handleBatch(messages);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("NATS audit consumer failed: {}", e.getMessage());
        }
    }

    private boolean shouldBackoff(JetStreamManagement jsm) {
        if (properties.maxStreamBytes() <= 0) {
            return false;
        }
        try {
            long bytes = jsm.getStreamInfo(properties.stream()).getStreamState().getByteCount();
            return bytes >= properties.maxStreamBytes();
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureStream(JetStreamManagement jsm) throws Exception {
        try {
            jsm.getStreamInfo(properties.stream());
        } catch (Exception notFound) {
            StreamConfiguration.Builder builder = StreamConfiguration.builder()
                    .name(properties.stream())
                    .subjects(properties.subject())
                    .storageType(StorageType.File)
                    .retentionPolicy(RetentionPolicy.Limits);
            if (properties.maxStreamBytes() > 0) {
                builder.maxBytes(properties.maxStreamBytes());
            }
            jsm.addStream(builder.build());
        }
    }

    private void handleBatch(List<Message> messages) {
        List<AuditRepository.AuditRow> rows = new ArrayList<>(messages.size());
        try {
            for (Message msg : messages) {
                String json = new String(msg.getData(), StandardCharsets.UTF_8);
                AuditEventMessage event = objectMapper.readValue(json, AuditEventMessage.class);
                String id = event.id() != null ? event.id() : UUID.randomUUID().toString();
                rows.add(new AuditRepository.AuditRow(
                        id,
                        event.tenantId(),
                        event.actorType(),
                        event.actorId(),
                        event.action(),
                        event.resource(),
                        event.resourceId(),
                        event.detailJson(),
                        event.ip()));
            }
            auditService.writeSyncBatch(rows);
            for (Message msg : messages) {
                msg.ack();
            }
        } catch (Exception e) {
            log.warn("NATS audit batch handling failed: {}", e.getMessage());
            for (Message msg : messages) {
                try {
                    msg.nak();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
