package com.sentinelhub.module.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.DeviceRepository;
import com.sentinelhub.module.policy.domain.Policy;
import com.sentinelhub.module.policy.domain.PolicyBundle;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final DeviceRepository deviceRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PolicyService(PolicyRepository policyRepository, DeviceRepository deviceRepository,
                         AuditService auditService, ObjectMapper objectMapper) {
        this.policyRepository = policyRepository;
        this.deviceRepository = deviceRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listPolicies(String tenantId) {
        return policyRepository.listByTenant(tenantId).stream().map(this::toView).toList();
    }

    public Map<String, Object> getPolicy(String tenantId, String id) {
        Policy p = policyRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("policy not found"));
        return toView(p);
    }

    public Map<String, Object> createPolicy(String tenantId, String userId, String name, String type,
                                              Map<String, Object> content, int priority) {
        String contentJson = toJson(content != null ? content : defaultContent(type));
        Policy p = policyRepository.insert(tenantId, name, type, contentJson, "{}", priority, userId);
        auditService.log(tenantId, "user", userId, "policy.create", "policy", p.id(),
                Map.of("name", name, "type", type), null);
        return toView(p);
    }

    public Map<String, Object> updatePolicy(String tenantId, String userId, String id, String name,
                                            Map<String, Object> content, int priority) {
        Policy existing = policyRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("policy not found"));
        if (!"draft".equals(existing.status())) {
            throw new IllegalArgumentException("only draft policies can be edited");
        }
        policyRepository.updateDraft(tenantId, id, name, toJson(content), priority);
        auditService.log(tenantId, "user", userId, "policy.update", "policy", id, Map.of("name", name), null);
        return getPolicy(tenantId, id);
    }

    public Map<String, Object> publishPolicy(String tenantId, String userId, String id) {
        Policy policy = policyRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("policy not found"));
        int version = policyRepository.nextVersion(id);
        policyRepository.insertVersion(tenantId, id, version, policy.contentJson(), userId);
        policyRepository.markPublished(tenantId, id);
        PolicyBundle bundle = rebuildTenantBundle(tenantId);
        auditService.log(tenantId, "user", userId, "policy.publish", "policy", id,
                Map.of("bundle_version", bundle.version()), null);
        return Map.of(
                "policy_id", id,
                "version", version,
                "bundle_version", bundle.version(),
                "bundle_hash", bundle.contentHash()
        );
    }

    public Map<String, Object> getBundleSummaryForClient(String clientId) {
        return deviceRepository.findByAgentIdAny(clientId)
                .flatMap(d -> policyRepository.findBundle(d.tenantId()))
                .map(b -> Map.<String, Object>of(
                        "version", b.version(),
                        "hash", b.contentHash()
                ))
                .orElse(Map.of());
    }

    public Map<String, Object> getFullBundleForClient(String clientId) {
        var device = deviceRepository.findByAgentIdAny(clientId)
                .orElseThrow(() -> new IllegalArgumentException("device not registered"));
        PolicyBundle bundle = policyRepository.findBundle(device.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("no policy bundle published"));
        Map<String, Object> content = parseJson(bundle.contentJson());
        return Map.of(
                "version", bundle.version(),
                "hash", bundle.contentHash(),
                "published_at", bundle.publishedAt().toString(),
                "rules", content.getOrDefault("rules", Map.of())
        );
    }

    public PolicyBundle rebuildTenantBundle(String tenantId) {
        List<Policy> published = policyRepository.listPublished(tenantId);
        Map<String, Object> rules = new LinkedHashMap<>();
        for (Policy p : published) {
            Map<String, Object> content = parseJson(p.contentJson());
            rules.put(p.type(), Map.of(
                    "policy_id", p.id(),
                    "name", p.name(),
                    "priority", p.priority(),
                    "config", content
            ));
        }
        Map<String, Object> bundleContent = Map.of(
                "tenant_id", tenantId,
                "generated_at", Instant.now().toString(),
                "rules", rules
        );
        String contentJson = toJson(bundleContent);
        String hash = sha256(contentJson);
        String version = "pol_" + Instant.now().getEpochSecond();
        policyRepository.upsertBundle(tenantId, version, contentJson, hash);
        return new PolicyBundle(tenantId, version, contentJson, hash, Instant.now());
    }

    public void seedDemoSoftwarePolicy(String tenantId, String userId) {
        if (!policyRepository.listByTenant(tenantId).isEmpty()) {
            return;
        }
        Map<String, Object> content = Map.of(
                "blacklist", List.of("utorrent.exe", "bittorrent.exe"),
                "whitelist", List.of(),
                "action", "alert"
        );
        Map<String, Object> created = createPolicy(tenantId, userId, "默认软件黑名单", "software", content, 100);
        publishPolicy(tenantId, userId, created.get("id").toString());
    }

    private Map<String, Object> toView(Policy p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("name", p.name());
        m.put("type", p.type());
        m.put("status", p.status());
        m.put("priority", p.priority());
        m.put("content", parseJson(p.contentJson()));
        m.put("created_at", p.createdAt().toString());
        m.put("updated_at", p.updatedAt().toString());
        return m;
    }

    private Map<String, Object> defaultContent(String type) {
        if ("software".equals(type)) {
            return Map.of("blacklist", List.of(), "whitelist", List.of(), "action", "alert");
        }
        return Map.of();
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid json");
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
