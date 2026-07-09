package com.sentinelhub.module.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PolicyScopeMatcher {

    private final ObjectMapper objectMapper;

    public PolicyScopeMatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean appliesToDevice(String scopeJson, String orgUnitId, Set<String> deviceGroupIds) {
        Map<String, Object> scope = parseScope(scopeJson);
        String mode = String.valueOf(scope.getOrDefault("mode", "all"));
        if (mode.isBlank() || "all".equals(mode)) {
            return true;
        }
        List<String> ids = extractIds(scope);
        if (ids.isEmpty()) {
            return false;
        }
        return switch (mode) {
            case "org_unit" -> orgUnitId != null && ids.contains(orgUnitId);
            case "device_group" -> deviceGroupIds.stream().anyMatch(ids::contains);
            default -> true;
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> extractIds(Map<String, Object> scope) {
        Object raw = scope.get("ids");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Map<String, Object> parseScope(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("mode", "all");
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of("mode", "all");
        }
    }
}
