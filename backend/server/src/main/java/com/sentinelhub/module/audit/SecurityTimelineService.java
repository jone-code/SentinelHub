package com.sentinelhub.module.audit;

import com.sentinelhub.config.TimelineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SecurityTimelineService {

    private static final Logger log = LoggerFactory.getLogger(SecurityTimelineService.class);

    private final ClickHouseSecurityTimelineRepository coldRepository;
    private final SecurityTimelineJdbcRepository hotRepository;
    private final TimelineProperties timelineProperties;

    public SecurityTimelineService(ClickHouseSecurityTimelineRepository coldRepository,
                                   SecurityTimelineJdbcRepository hotRepository,
                                   TimelineProperties timelineProperties) {
        this.coldRepository = coldRepository;
        this.hotRepository = hotRepository;
        this.timelineProperties = timelineProperties;
    }

    public List<Map<String, Object>> listForAdmin(String tenantId, int page, int pageSize,
                                                   String source, String storage) {
        int offset = Math.max(0, (page - 1) * pageSize);
        if ("hot".equalsIgnoreCase(storage)) {
            return hotRepository.list(tenantId, pageSize, offset, source);
        }
        if ("cold".equalsIgnoreCase(storage)) {
            return listColdOrFallback(tenantId, pageSize, offset, source);
        }
        if ("auto".equalsIgnoreCase(storage)) {
            if (coldRepository.isEnabled()) {
                try {
                    return coldRepository.list(tenantId, pageSize, offset, source);
                } catch (Exception e) {
                    log.warn("ClickHouse timeline query failed, falling back to hot: {}", e.getMessage());
                }
            }
            return hotRepository.list(tenantId, pageSize, offset, source);
        }
        return hotRepository.list(tenantId, pageSize, offset, source);
    }

    public int count(String tenantId, String source, String storage) {
        if ("hot".equalsIgnoreCase(storage)) {
            return hotRepository.count(tenantId, source);
        }
        if ("cold".equalsIgnoreCase(storage)) {
            return countColdOrFallback(tenantId, source);
        }
        if ("auto".equalsIgnoreCase(storage)) {
            if (coldRepository.isEnabled()) {
                try {
                    return coldRepository.count(tenantId, source);
                } catch (Exception e) {
                    log.warn("ClickHouse timeline count failed, falling back to hot: {}", e.getMessage());
                }
            }
            return hotRepository.count(tenantId, source);
        }
        return hotRepository.count(tenantId, source);
    }

    private List<Map<String, Object>> listColdOrFallback(String tenantId, int pageSize, int offset, String source) {
        if (!coldRepository.isEnabled()) {
            return timelineProperties.fallbackToHot()
                    ? hotRepository.list(tenantId, pageSize, offset, source)
                    : List.of();
        }
        try {
            return coldRepository.list(tenantId, pageSize, offset, source);
        } catch (Exception e) {
            log.warn("ClickHouse timeline query failed: {}", e.getMessage());
            return timelineProperties.fallbackToHot()
                    ? hotRepository.list(tenantId, pageSize, offset, source)
                    : List.of();
        }
    }

    private int countColdOrFallback(String tenantId, String source) {
        if (!coldRepository.isEnabled()) {
            return timelineProperties.fallbackToHot() ? hotRepository.count(tenantId, source) : 0;
        }
        try {
            return coldRepository.count(tenantId, source);
        } catch (Exception e) {
            log.warn("ClickHouse timeline count failed: {}", e.getMessage());
            return timelineProperties.fallbackToHot() ? hotRepository.count(tenantId, source) : 0;
        }
    }
}
