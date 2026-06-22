package com.smartlab.erp.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmClient {

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    public String chatCompletion(String systemPrompt, String userPrompt) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("LLM API key is not configured");
        }

        String url = properties.getBaseUrl().replaceAll("/+$", "");
        if (!url.endsWith("/v1")) {
            url = url + "/v1";
        }
        url = url + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.1
        );

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(new org.springframework.http.converter.StringHttpMessageConverter());
        restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory())
                .setConnectTimeout(Duration.ofSeconds(10));
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory())
                .setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                return message.path("content").asText("").trim();
            }
            throw new RuntimeException("LLM response did not contain choices");
        } catch (Exception e) {
            log.error("LLM request failed: {}", e.getMessage(), e);
            throw new RuntimeException("LLM request failed: " + e.getMessage(), e);
        }
    }
}
