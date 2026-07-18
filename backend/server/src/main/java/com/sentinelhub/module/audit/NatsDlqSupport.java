package com.sentinelhub.module.audit;

import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NatsDlqSupport {

    private static final Logger log = LoggerFactory.getLogger(NatsDlqSupport.class);

    private NatsDlqSupport() {
    }

    public static void ensureDlqStream(JetStreamManagement jsm, String dlqStream, String dlqSubject) throws Exception {
        if (dlqStream == null || dlqStream.isBlank()) {
            return;
        }
        try {
            jsm.getStreamInfo(dlqStream);
        } catch (Exception notFound) {
            StreamConfiguration sc = StreamConfiguration.builder()
                    .name(dlqStream)
                    .subjects(dlqSubject)
                    .storageType(StorageType.File)
                    .retentionPolicy(RetentionPolicy.Limits)
                    .build();
            jsm.addStream(sc);
        }
    }

    public static int deliveredCount(Message msg) {
        if (msg.metaData() == null) {
            return 1;
        }
        return (int) msg.metaData().deliveredCount();
    }

    public static void handleFailure(JetStream js,
                                     JetStreamManagement jsm,
                                     String dlqStream,
                                     String dlqSubject,
                                     int maxDeliver,
                                     Message msg) {
        int delivered = deliveredCount(msg);
        if (delivered >= maxDeliver) {
            publishToDlq(js, jsm, dlqStream, dlqSubject, msg);
            try {
                msg.term();
            } catch (Exception e) {
                try {
                    msg.ack();
                } catch (Exception ignored) {
                }
            }
        } else {
            try {
                msg.nak();
            } catch (Exception ignored) {
            }
        }
    }

    private static void publishToDlq(JetStream js,
                                     JetStreamManagement jsm,
                                     String dlqStream,
                                     String dlqSubject,
                                     Message msg) {
        if (dlqStream == null || dlqStream.isBlank()) {
            return;
        }
        try {
            ensureDlqStream(jsm, dlqStream, dlqSubject);
            js.publish(dlqSubject, msg.getData());
        } catch (Exception e) {
            log.warn("NATS DLQ publish failed: {}", e.getMessage());
        }
    }
}
