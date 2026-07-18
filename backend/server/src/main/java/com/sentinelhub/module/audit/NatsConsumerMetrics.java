package com.sentinelhub.module.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class NatsConsumerMetrics {

    private final Counter auditBatchesProcessed;
    private final Counter auditMessagesProcessed;
    private final Counter auditBatchesFailed;
    private final Counter auditMessagesDlq;
    private final Counter auditBackoffCount;

    private final Counter clientEventBatchesProcessed;
    private final Counter clientEventMessagesProcessed;
    private final Counter clientEventBatchesFailed;
    private final Counter clientEventMessagesDlq;
    private final Counter clientEventBackoffCount;

    public NatsConsumerMetrics(MeterRegistry registry) {
        auditBatchesProcessed = registry.counter("sentinel.nats.audit.batches.processed");
        auditMessagesProcessed = registry.counter("sentinel.nats.audit.messages.processed");
        auditBatchesFailed = registry.counter("sentinel.nats.audit.batches.failed");
        auditMessagesDlq = registry.counter("sentinel.nats.audit.messages.dlq");
        auditBackoffCount = registry.counter("sentinel.nats.audit.backoff.count");

        clientEventBatchesProcessed = registry.counter("sentinel.nats.client_events.batches.processed");
        clientEventMessagesProcessed = registry.counter("sentinel.nats.client_events.messages.processed");
        clientEventBatchesFailed = registry.counter("sentinel.nats.client_events.batches.failed");
        clientEventMessagesDlq = registry.counter("sentinel.nats.client_events.messages.dlq");
        clientEventBackoffCount = registry.counter("sentinel.nats.client_events.backoff.count");
    }

    public void recordAuditBatchSuccess(int size) {
        auditBatchesProcessed.increment();
        auditMessagesProcessed.increment(size);
    }

    public void recordAuditBatchFailure(int size) {
        auditBatchesFailed.increment();
    }

    public void recordAuditDlq(int count) {
        auditMessagesDlq.increment(count);
    }

    public void recordAuditBackoff() {
        auditBackoffCount.increment();
    }

    public void recordClientEventBatchSuccess(int size) {
        clientEventBatchesProcessed.increment();
        clientEventMessagesProcessed.increment(size);
    }

    public void recordClientEventBatchFailure(int size) {
        clientEventBatchesFailed.increment();
    }

    public void recordClientEventDlq(int count) {
        clientEventMessagesDlq.increment(count);
    }

    public void recordClientEventBackoff() {
        clientEventBackoffCount.increment();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("audit", Map.of(
                "batches_processed", (long) auditBatchesProcessed.count(),
                "messages_processed", (long) auditMessagesProcessed.count(),
                "batches_failed", (long) auditBatchesFailed.count(),
                "messages_dlq", (long) auditMessagesDlq.count(),
                "backoff_count", (long) auditBackoffCount.count()));
        out.put("client_events", Map.of(
                "batches_processed", (long) clientEventBatchesProcessed.count(),
                "messages_processed", (long) clientEventMessagesProcessed.count(),
                "batches_failed", (long) clientEventBatchesFailed.count(),
                "messages_dlq", (long) clientEventMessagesDlq.count(),
                "backoff_count", (long) clientEventBackoffCount.count()));
        return out;
    }
}
