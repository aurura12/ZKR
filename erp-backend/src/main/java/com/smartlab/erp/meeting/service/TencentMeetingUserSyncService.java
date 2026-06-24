package com.smartlab.erp.meeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.meeting.config.TencentMeetingConfig;
import com.smartlab.erp.meeting.entity.TencentUserMapping;
import com.smartlab.erp.meeting.repository.TencentUserMappingRepository;
import com.smartlab.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TencentMeetingUserSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PAGE_SIZE = 100;

    /**
     * 默认操作者 ID（腾讯会议超级管理员），用于首次拉取等场景
     */
    private static final String DEFAULT_OPERATOR_ID = "admin1780452520";

    private final TencentMeetingConfig config;
    private final TencentMeetingSignatureService signatureService;
    private final TencentUserMappingRepository mappingRepository;
    private final UserRepository userRepository;

    /**
     * 获取用于调用 API 的操作者 ID（Tencent Meeting userid）
     * 优先从映射表中取已绑定用户的 tencent_user_id，否则使用默认操作者 ID
     */
    private String resolveOperatorId() {
        return mappingRepository.findAll().stream()
                .findFirst()
                .map(TencentUserMapping::getTencentUserId)
                .orElse(DEFAULT_OPERATOR_ID);
    }

    /**
     * 获取已映射的腾讯会议用户列表（供参会人选单使用）
     */
    public List<Map<String, String>> getMappedUsers() {
        return mappingRepository.findAll().stream()
                .map(mapping -> {
                    Map<String, String> item = new java.util.LinkedHashMap<>();
                    item.put("userId", mapping.getErpUserId());
                    item.put("tencentUserId", mapping.getTencentUserId());
                    String name = mapping.getTencentUsername();
                    if (name == null || name.isBlank()) {
                        name = userRepository.findById(mapping.getErpUserId())
                                .map(User::getName)
                                .orElse(mapping.getErpUserId());
                    }
                    item.put("name", name);
                    return item;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 直接从腾讯会议拉取所有用户列表（用于绑定时校验 userid 存在性、创建会议时选择参会人）
     */
    public List<Map<String, String>> listAllTmUsers() {
        String operatorId = resolveOperatorId();

        List<Map<String, String>> result = new ArrayList<>();
        String pos = "";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        try {
            do {
                String queryString = "operator_id=" + urlEncode(operatorId)
                        + "&operator_id_type=1"
                        + "&size=" + PAGE_SIZE;
                if (!pos.isEmpty()) {
                    queryString += "&pos=" + urlEncode(pos);
                }
                String fullUrl = "/v1/users/advance/list?" + queryString;

                Map<String, String> headers = signatureService.generateCommonHeaders();
                String signature = signatureService.calculateSignature("GET", fullUrl, "", headers);

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(config.getApiBaseUrl() + fullUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("AppId", headers.get("AppId"))
                        .header("X-TC-Key", headers.get("X-TC-Key"))
                        .header("X-TC-Timestamp", headers.get("X-TC-Timestamp"))
                        .header("X-TC-Nonce", headers.get("X-TC-Nonce"))
                        .header("X-TC-Signature", signature)
                        .header("X-TC-Registered", "1");
                if (headers.containsKey("SdkId")) {
                    requestBuilder.header("SdkId", headers.get("SdkId"));
                }
                HttpResponse<String> response = client.send(requestBuilder.GET().build(),
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) break;

                JsonNode root = MAPPER.readTree(response.body());
                JsonNode usersNode = root.get("users");
                if (usersNode != null && usersNode.isArray()) {
                    for (JsonNode userNode : usersNode) {
                        Map<String, String> item = new java.util.LinkedHashMap<>();
                        item.put("userId", userNode.path("userid").asText());
                        item.put("name", userNode.path("username").asText());
                        item.put("accountType", String.valueOf(userNode.path("user_account_type").asInt(0)));
                        result.add(item);
                    }
                }
                boolean hasRemaining = root.path("has_remaining").asBoolean(false);
                pos = hasRemaining ? root.path("next_pos").asText("") : "";
            } while (!pos.isEmpty());
        } catch (Exception e) {
            log.warn("[TencentMeetingSync] 拉取腾讯会议用户列表异常", e);
        }
        return result;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
