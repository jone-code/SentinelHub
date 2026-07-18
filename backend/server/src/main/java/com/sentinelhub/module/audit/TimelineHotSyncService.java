package com.sentinelhub.module.audit;

import com.sentinelhub.config.AuditClickHouseProperties;
import com.sentinelhub.config.TimelineSyncProperties;
import com.sentinelhub.module.software.ClickHouseClientEventRepository;
import com.sentinelhub.module.software.ClientEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class TimelineHotSyncService {

    private static final Logger log = LoggerFactory.getLogger(TimelineHotSyncService.class);
    private static final String KEY_AUDIT = "audit_logs";
    private static final String KEY_CLIENT_EVENTS = "client_events";

    private final TimelineSyncProperties properties;
    private final AuditClickHouseProperties clickHouseProperties;
    private final TimelineSyncStateRepository syncStateRepository;
    private final AuditRepository auditRepository;
    private final ClickHouseAuditRepository clickHouseAuditRepository;
    private final ClientEventRepository clientEventRepository;
    private final ClickHouseClientEventRepository clickHouseClientEventRepository;

    public TimelineHotSyncService(TimelineSyncProperties properties,
                                  AuditClickHouseProperties clickHouseProperties,
                                  TimelineSyncStateRepository syncStateRepository,
                                  AuditRepository auditRepository,
                                  ClickHouseAuditRepository clickHouseAuditRepository,
                                  ClientEventRepository clientEventRepository,
                                  ClickHouseClientEventRepository clickHouseClientEventRepository) {
        this.properties = properties;
        this.clickHouseProperties = clickHouseProperties;
        this.syncStateRepository = syncStateRepository;
        this.auditRepository = auditRepository;
        this.clickHouseAuditRepository = clickHouseAuditRepository;
        this.clientEventRepository = clientEventRepository;
        this.clickHouseClientEventRepository = clickHouseClientEventRepository;
    }

    @Scheduled(fixedDelayString = "${sentinelhub.audit.timeline-sync.interval-ms:60000}")
    public void syncHotToCold() {
        if (!properties.enabled() || !clickHouseProperties.enabled()) {
            return;
        }
        try {
            syncAuditLogs();
            syncClientEvents();
        } catch (Exception e) {
            log.warn("Timeline hot-to-cold sync failed: {}", e.getMessage());
        }
    }

    private void syncAuditLogs() {
        Instant watermark = syncStateRepository.getWatermark(KEY_AUDIT);
        List<Map<String, Object>> rows = auditRepository.listAfterWatermark(
                Timestamp.from(watermark), properties.batchSize());
        if (rows.isEmpty()) {
            return;
        }
        List<AuditRepository.AuditRow> batch = rows.stream()
                .map(row -> new AuditRepository.AuditRow(
                        String.valueOf(row.get("id")),
                        String.valueOf(row.get("tenant_id")),
                        stringVal(row.get("actor_type")),
                        stringVal(row.get("actor_id")),
                        stringVal(row.get("action")),
                        stringVal(row.get("resource")),
                        stringVal(row.get("resource_id")),
                        stringVal(row.get("detail")),
                        stringVal(row.get("ip_address"))))
                .toList();
        clickHouseAuditRepository.batchInsert(batch);
        Instant newWatermark = maxCreatedAt(rows);
        if (newWatermark.isAfter(watermark)) {
            syncStateRepository.setWatermark(KEY_AUDIT, newWatermark);
        }
        log.debug("Synced {} audit_logs rows to ClickHouse", rows.size());
    }

    private void syncClientEvents() {
        Instant watermark = syncStateRepository.getWatermark(KEY_CLIENT_EVENTS);
        List<Map<String, Object>> rows = clientEventRepository.listAfterWatermark(
                Timestamp.from(watermark), properties.batchSize());
        if (rows.isEmpty()) {
            return;
        }
        List<ClientEventRepository.ClientEventRow> batch = rows.stream()
                .map(row -> new ClientEventRepository.ClientEventRow(
                        String.valueOf(row.get("id")),
                        String.valueOf(row.get("tenant_id")),
                        String.valueOf(row.get("device_id")),
                        stringVal(row.get("event_type")),
                        stringVal(row.get("severity")),
                        stringVal(row.get("detail"))))
                .toList();
        clickHouseClientEventRepository.batchInsert(batch);
        Instant newWatermark = maxCreatedAt(rows);
        if (newWatermark.isAfter(watermark)) {
            syncStateRepository.setWatermark(KEY_CLIENT_EVENTS, newWatermark);
        }
        log.debug("Synced {} client_events rows to ClickHouse", rows.size());
    }

    @SuppressWarnings("unchecked")
    private static Instant maxCreatedAt(List<Map<String, Object>> rows) {
        Instant max = Instant.EPOCH;
        for (Map<String, Object> row : rows) {
            Object createdAt = row.get("created_at");
            Instant instant;
            if (createdAt instanceof Timestamp ts) {
                instant = ts.toInstant();
            } else if (createdAt instanceof java.util.Date d) {
                instant = d.toInstant();
            } else {
                instant = Instant.parse(String.valueOf(createdAt));
            }
            if (instant.isAfter(max)) {
                max = instant;
            }
        }
        return max;
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : null;
    }
}
