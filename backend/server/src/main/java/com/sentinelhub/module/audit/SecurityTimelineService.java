package com.sentinelhub.module.audit;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SecurityTimelineService {

    private final ClickHouseSecurityTimelineRepository timelineRepository;

    public SecurityTimelineService(ClickHouseSecurityTimelineRepository timelineRepository) {
        this.timelineRepository = timelineRepository;
    }

    public List<Map<String, Object>> listForAdmin(String tenantId, int page, int pageSize,
                                                   String source, String storage) {
        if (!"cold".equalsIgnoreCase(storage) || !timelineRepository.isEnabled()) {
            return List.of();
        }
        int offset = Math.max(0, (page - 1) * pageSize);
        return timelineRepository.list(tenantId, pageSize, offset, source);
    }

    public int count(String tenantId, String source, String storage) {
        if (!"cold".equalsIgnoreCase(storage) || !timelineRepository.isEnabled()) {
            return 0;
        }
        return timelineRepository.count(tenantId, source);
    }
}
