package com.sentinelhub.module.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.BillingWebhookProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class BillingWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookClient.class);

    private final BillingWebhookProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public BillingWebhookClient(BillingWebhookProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<String> notifyPlanChangeApplied(String tenantId, String requestId,
                                                    String fromTier, String toTier,
                                                    int monthlyPriceCents, String currency) {
        if (!properties.enabled() || properties.url() == null || properties.url().isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "plan_change.applied");
            payload.put("tenant_id", tenantId);
            payload.put("request_id", requestId);
            payload.put("from_tier", fromTier);
            payload.put("to_tier", toTier);
            payload.put("monthly_price_cents", monthlyPriceCents);
            payload.put("currency", currency);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.url()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (properties.secret() != null && !properties.secret().isBlank()) {
                builder.header("X-Billing-Secret", properties.secret());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Billing webhook failed: HTTP {}", response.statusCode());
                return Optional.empty();
            }
            String externalId = "wh-" + requestId.substring(0, 8);
            log.info("Billing webhook delivered for tenant {} request {}", tenantId, requestId);
            return Optional.of(externalId);
        } catch (Exception e) {
            log.warn("Billing webhook error: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
