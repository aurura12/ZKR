package com.smartlab.erp.meeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.meeting.config.TencentMeetingConfig;
import com.smartlab.erp.meeting.exception.TencentMeetingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
public class TencentMeetingTokenService {

    private static final String TOKEN_URL = "/openapi/v3/api/v1/auth/access_token";
    private static final Duration TOKEN_EXPIRE_BUFFER = Duration.ofMinutes(5);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TencentMeetingConfig config;
    private String cachedToken = null;
    private Instant tokenExpiresAt = Instant.MIN;

    public TencentMeetingTokenService(TencentMeetingConfig config) {
        this.config = config;
    }

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
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            String body = String.format(
                    "{\"app_id\":\"%s\",\"secret\":\"%s\"}",
                    config.getSecretId(),
                    config.getSecretKey()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiBaseUrl() + TOKEN_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TencentMeetingException(
                        "获取Token",
                        "腾讯会议 token 接口返回异常: HTTP " + response.statusCode(),
                        null,
                        response.body()
                );
            }

            JsonNode node = MAPPER.readTree(response.body());

            // 腾讯会议返回格式: {"code": 0, "data": {"access_token": "xxx", "expire_in": 7200}}
            if (node.has("code") && node.get("code").asInt() == 0 && node.has("data")) {
                JsonNode data = node.get("data");
                if (data.hasNonNull("access_token")) {
                    cachedToken = data.get("access_token").asText();
                    long expireIn = data.path("expire_in").asLong(7200L);
                    tokenExpiresAt = Instant.now().plusSeconds(Math.max(60L, expireIn));
                    log.info("[TencentMeeting] Token refreshed successfully, expires at {}", tokenExpiresAt);
                    return;
                }
            }

            int errcode = node.has("code") ? node.get("code").asInt() : -1;
            String errmsg = node.has("message") ? node.get("message").asText() : response.body();
            throw new TencentMeetingException(
                    "获取Token",
                    "腾讯会议 token 刷新失败: " + errmsg,
                    errcode >= 0 ? errcode : null,
                    response.body()
            );

        } catch (TencentMeetingException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TencentMeeting] Token refresh failed", e);
            throw new TencentMeetingException(
                    "获取Token",
                    "腾讯会议 token 刷新失败: " + e.getMessage(),
                    null,
                    null,
                    e
            );
        }
    }
}
