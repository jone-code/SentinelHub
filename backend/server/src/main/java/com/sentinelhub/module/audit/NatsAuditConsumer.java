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
import java.util.List;

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
        log.info("NATS async audit consumer started (subject={})", properties.subject());
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
                sub.pull(10);
                List<Message> messages = sub.fetch(10, Duration.ofSeconds(2));
                for (Message msg : messages) {
                    handleMessage(msg);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("NATS audit consumer failed: {}", e.getMessage());
        }
    }

    private void ensureStream(JetStreamManagement jsm) throws Exception {
        try {
            jsm.getStreamInfo(properties.stream());
        } catch (Exception notFound) {
            StreamConfiguration sc = StreamConfiguration.builder()
                    .name(properties.stream())
                    .subjects(properties.subject())
                    .storageType(StorageType.File)
                    .retentionPolicy(RetentionPolicy.Limits)
                    .build();
            jsm.addStream(sc);
        }
    }

    private void handleMessage(Message msg) {
        try {
            String json = new String(msg.getData(), StandardCharsets.UTF_8);
            AuditEventMessage event = objectMapper.readValue(json, AuditEventMessage.class);
            auditService.writeSync(
                    event.tenantId(),
                    event.actorType(),
                    event.actorId(),
                    event.action(),
                    event.resource(),
                    event.resourceId(),
                    event.detailJson(),
                    event.ip());
            msg.ack();
        } catch (Exception e) {
            log.warn("NATS audit message handling failed: {}", e.getMessage());
            try {
                msg.nak();
            } catch (Exception ignored) {
            }
        }
    }
}
