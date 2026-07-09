package com.sentinelhub.module.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.AiLlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AiLlmClient {

    private static final Logger log = LoggerFactory.getLogger(AiLlmClient.class);

    private final AiLlmProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiLlmClient(AiLlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Optional<String> summarizeInsights(List<Map<String, Object>> insights) {
        if (!properties.isConfigured() || insights.isEmpty()) {
            return Optional.empty();
        }
        try {
            String context = objectMapper.writeValueAsString(insights.stream()
                    .map(i -> Map.of(
                            "title", i.getOrDefault("title", ""),
                            "severity", i.getOrDefault("severity", ""),
                            "summary", i.getOrDefault("summary", ""),
                            "type", i.getOrDefault("insight_type", "")
                    ))
                    .toList());

            String prompt = """
                    你是企业安全分析师。根据以下 JSON 安全洞察列表，用 2-4 句中文给出：
                    1) 当前最大风险
                    2) 建议优先处置动作
                    洞察数据：
                    """ + context;

            Map<String, Object> body = Map.of(
                    "model", properties.model() != null ? properties.model() : "gpt-4o-mini",
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "max_tokens", 300,
                    "temperature", 0.3
            );

            String url = properties.baseUrl().replaceAll("/$", "") + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("LLM API returned {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            return content != null && !content.isBlank() ? Optional.of(content.trim()) : Optional.empty();
        } catch (Exception e) {
            log.warn("LLM summarize failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
