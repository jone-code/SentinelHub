package com.sentinelhub.module.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private final AiRepository aiRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AiLlmClient aiLlmClient;

    public AiService(AiRepository aiRepository, AuditService auditService, ObjectMapper objectMapper,
                     AiLlmClient aiLlmClient) {
        this.aiRepository = aiRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.aiLlmClient = aiLlmClient;
    }

    public Map<String, Object> runAnalysis(String tenantId, String userId) {
        int created = 0;
        created += analyzeEventClusters(tenantId);
        created += analyzeLowCompliance(tenantId);
        created += analyzeNacRisk(tenantId);

        auditService.log(tenantId, "user", userId, "ai.analysis.run", "tenant", tenantId,
                Map.of("insights_created", created), null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("insights_created", created);
        result.put("open_insights", aiRepository.countByTenant(tenantId, "open"));
        result.put("llm_enabled", true);

        List<Map<String, Object>> open = listInsightsForAdmin(tenantId, "open", 1, 10).stream()
                .filter(i -> !"llm_summary".equals(i.get("insight_type")))
                .toList();
        aiLlmClient.summarizeInsights(open).ifPresent(summary -> {
            result.put("llm_summary", summary);
            aiRepository.resolveOpenByType(tenantId, "llm_summary");
            aiRepository.insertInsight(tenantId, "llm_summary", "info", "AI 分析摘要",
                    summary, toJson(Map.of("source", "llm")), null);
        });

        return result;
    }

    public Map<String, Object> overviewForAdmin(String tenantId) {
        return Map.of(
                "open", aiRepository.countByTenant(tenantId, "open"),
                "total", aiRepository.countByTenant(tenantId, null)
        );
    }

    public List<Map<String, Object>> listInsightsForAdmin(String tenantId, String status, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return aiRepository.listByTenant(tenantId, status, pageSize, offset).stream()
                .map(this::toInsightView)
                .toList();
    }

    public int countInsights(String tenantId, String status) {
        return aiRepository.countByTenant(tenantId, status);
    }

    public Map<String, Object> resolveInsight(String tenantId, String userId, String id) {
        aiRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("insight not found"));
        aiRepository.updateStatus(tenantId, id, "resolved");
        auditService.log(tenantId, "user", userId, "ai.insight.resolve", "ai_insight", id, Map.of(), null);
        return aiRepository.findById(tenantId, id).map(this::toInsightView).orElseThrow();
    }

    private int analyzeEventClusters(String tenantId) {
        int created = 0;
        Map<String, List<Map<String, Object>>> byDevice = new HashMap<>();
        for (Map<String, Object> row : aiRepository.countEventsByDevice(tenantId, 24)) {
            String deviceId = row.get("device_id").toString();
            byDevice.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(row);
        }
        for (var entry : byDevice.entrySet()) {
            String deviceId = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            int total = rows.stream().mapToInt(r -> ((Number) r.get("event_count")).intValue()).sum();
            if (total < 5) continue;

            String hostname = rows.getFirst().get("hostname") != null
                    ? rows.getFirst().get("hostname").toString() : deviceId;
            aiRepository.resolveOpenByType(tenantId, "event_cluster");
            aiRepository.insertInsight(
                    tenantId, "event_cluster", "high",
                    "设备异常事件聚类: " + hostname,
                    String.format("过去 24 小时检测到 %d 条安全事件，建议立即排查。", total),
                    toJson(Map.of("device_id", deviceId, "events", rows)),
                    deviceId);
            created++;
        }
        return created;
    }

    private int analyzeLowCompliance(String tenantId) {
        int created = 0;
        List<Map<String, Object>> devices = aiRepository.listLowComplianceDevices(tenantId, 60);
        if (devices.isEmpty()) return 0;

        aiRepository.resolveOpenByType(tenantId, "low_compliance");
        for (Map<String, Object> device : devices) {
            String deviceId = device.get("id").toString();
            String hostname = device.get("hostname") != null ? device.get("hostname").toString() : deviceId;
            int score = device.get("compliance_score") instanceof Number n ? n.intValue() : 0;
            aiRepository.insertInsight(
                    tenantId, "low_compliance", score < 40 ? "high" : "warning",
                    "低合规设备: " + hostname,
                    String.format("合规评分 %d，低于企业基线要求。", score),
                    toJson(Map.of("device_id", deviceId, "compliance_score", score,
                            "trust_score", device.get("trust_score"))),
                    deviceId);
            created++;
        }
        return created;
    }

    private int analyzeNacRisk(String tenantId) {
        int riskCount = aiRepository.countNacRiskDevices(tenantId);
        if (riskCount < 1) return 0;

        aiRepository.resolveOpenByType(tenantId, "nac_risk");
        aiRepository.insertInsight(
                tenantId, "nac_risk", "warning",
                "网络准入风险设备",
                String.format("当前有 %d 台设备处于受限或拒绝入网状态。", riskCount),
                toJson(Map.of("restricted_or_denied_count", riskCount)),
                null);
        return 1;
    }

    private Map<String, Object> toInsightView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.get("id"));
        view.put("insight_type", row.get("insight_type"));
        view.put("severity", row.get("severity"));
        view.put("title", row.get("title"));
        view.put("summary", row.get("summary"));
        view.put("evidence", parseJson(row.get("evidence")));
        view.put("related_device_id", row.get("related_device_id"));
        view.put("hostname", row.get("hostname"));
        view.put("agent_id", row.get("agent_id"));
        view.put("status", row.get("status"));
        view.put("created_at", row.get("created_at").toString());
        view.put("updated_at", row.get("updated_at").toString());
        return view;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(Object raw) {
        if (raw == null) return Map.of();
        try {
            if (raw instanceof String s) return objectMapper.readValue(s, Map.class);
            return objectMapper.convertValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
