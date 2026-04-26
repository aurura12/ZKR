package com.smartlab.erp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.exception.DingTalkIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
public class DingTalkTokenService {

    @Value("${dingtalk.app-key}")
    private String appKey;

    @Value("${dingtalk.app-secret}")
    private String appSecret;

    private static final String TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
    private static final Duration TOKEN_EXPIRE_BUFFER = Duration.ofMinutes(5);

    private String cachedToken = null;
    private Instant tokenExpiresAt = Instant.MIN;

    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minus(TOKEN_EXPIRE_BUFFER))) {
            return cachedToken;
        }
        refreshAccessToken();
        return cachedToken;
    }

    public synchronized String refreshAccessToken() {
        refreshToken();
        return cachedToken;
    }

    public synchronized void invalidateToken() {
        cachedToken = null;
        tokenExpiresAt = Instant.MIN;
    }

    private void refreshToken() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            String body = String.format("{\"appKey\":\"%s\",\"appSecret\":\"%s\"}", appKey, appSecret);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DingTalkIntegrationException("获取钉钉 access token", "钉钉 token 接口返回异常: HTTP " + response.statusCode(), null, response.body());
            }

            JsonNode node = new ObjectMapper().readTree(response.body());
            if (node.hasNonNull("accessToken")) {
                cachedToken = node.get("accessToken").asText();
                long expireIn = node.path("expireIn").asLong(7200L);
                tokenExpiresAt = Instant.now().plusSeconds(Math.max(60L, expireIn));
                log.info("[DingTalk] Token refreshed successfully, expires at {}", tokenExpiresAt);
                return;
            }

            int errcode = node.has("errcode") ? node.get("errcode").asInt() : -1;
            String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : response.body();
            throw new DingTalkIntegrationException("获取钉钉 access token", "钉钉 token 刷新失败: " + errmsg, errcode >= 0 ? errcode : null, response.body());
        } catch (Exception e) {
            if (e instanceof DingTalkIntegrationException dingTalkIntegrationException) {
                throw dingTalkIntegrationException;
            }
            log.error("[DingTalk] Token refresh failed", e);
            throw new DingTalkIntegrationException("获取钉钉 access token", "DingTalk token 刷新失败: " + e.getMessage(), null, null, e);
        }
    }
}
