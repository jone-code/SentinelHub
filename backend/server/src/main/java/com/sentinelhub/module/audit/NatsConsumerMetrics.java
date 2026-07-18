package com.sentinelhub.module.audit;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class NatsConsumerMetrics {

    private final AtomicLong auditBatchesProcessed = new AtomicLong();
    private final AtomicLong auditMessagesProcessed = new AtomicLong();
    private final AtomicLong auditBatchesFailed = new AtomicLong();
    private final AtomicLong auditMessagesDlq = new AtomicLong();
    private final AtomicLong auditBackoffCount = new AtomicLong();

    private final AtomicLong clientEventBatchesProcessed = new AtomicLong();
    private final AtomicLong clientEventMessagesProcessed = new AtomicLong();
    private final AtomicLong clientEventBatchesFailed = new AtomicLong();
    private final AtomicLong clientEventMessagesDlq = new AtomicLong();
    private final AtomicLong clientEventBackoffCount = new AtomicLong();

    public void recordAuditBatchSuccess(int size) {
        auditBatchesProcessed.incrementAndGet();
        auditMessagesProcessed.addAndGet(size);
    }

    public void recordAuditBatchFailure(int size) {
        auditBatchesFailed.incrementAndGet();
    }

    public void recordAuditDlq(int count) {
        auditMessagesDlq.addAndGet(count);
    }

    public void recordAuditBackoff() {
        auditBackoffCount.incrementAndGet();
    }

    public void recordClientEventBatchSuccess(int size) {
        clientEventBatchesProcessed.incrementAndGet();
        clientEventMessagesProcessed.addAndGet(size);
    }

    public void recordClientEventBatchFailure(int size) {
        clientEventBatchesFailed.incrementAndGet();
    }

    public void recordClientEventDlq(int count) {
        clientEventMessagesDlq.addAndGet(count);
    }

    public void recordClientEventBackoff() {
        clientEventBackoffCount.incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("audit", Map.of(
                "batches_processed", auditBatchesProcessed.get(),
                "messages_processed", auditMessagesProcessed.get(),
                "batches_failed", auditBatchesFailed.get(),
                "messages_dlq", auditMessagesDlq.get(),
                "backoff_count", auditBackoffCount.get()));
        out.put("client_events", Map.of(
                "batches_processed", clientEventBatchesProcessed.get(),
                "messages_processed", clientEventMessagesProcessed.get(),
                "batches_failed", clientEventBatchesFailed.get(),
                "messages_dlq", clientEventMessagesDlq.get(),
                "backoff_count", clientEventBackoffCount.get()));
        return out;
    }
}
