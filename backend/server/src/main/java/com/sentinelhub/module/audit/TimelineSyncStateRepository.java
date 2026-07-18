package com.sentinelhub.module.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class TimelineSyncStateRepository {

    private final JdbcTemplate jdbc;

    public TimelineSyncStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Instant getWatermark(String syncKey) {
        Optional<Timestamp> ts = jdbc.query(
                "SELECT watermark_at FROM timeline_sync_state WHERE sync_key = ?",
                rs -> rs.next() ? Optional.of(rs.getTimestamp("watermark_at")) : Optional.empty(),
                syncKey);
        return ts.map(Timestamp::toInstant).orElse(Instant.EPOCH);
    }

    public void setWatermark(String syncKey, Instant watermark) {
        jdbc.update(
                "INSERT INTO timeline_sync_state (sync_key, watermark_at) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE watermark_at = VALUES(watermark_at)",
                syncKey, Timestamp.from(watermark));
    }
}
