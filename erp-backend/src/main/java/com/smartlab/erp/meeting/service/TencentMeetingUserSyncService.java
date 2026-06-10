package com.smartlab.erp.meeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.meeting.config.TencentMeetingConfig;
import com.smartlab.erp.meeting.entity.TencentUserMapping;
import com.smartlab.erp.meeting.repository.TencentUserMappingRepository;
import com.smartlab.erp.repository.UserRepository;
import jakarta.annotation.PostConstruct;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TencentMeetingUserSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PAGE_SIZE = 100;

    private final TencentMeetingConfig config;
    private final TencentMeetingSignatureService signatureService;
    private final TencentUserMappingRepository mappingRepository;
    private final UserRepository userRepository;

    /**
     * 应用启动后自动执行一次用户同步
     */
    @PostConstruct
    public void autoSyncOnStartup() {
        log.info("[TencentMeetingSync] 应用启动，开始自动同步腾讯会议用户...");
        try {
            SyncResult result = syncUsers();
            log.info("[TencentMeetingSync] 启动同步完成: 匹配={}, 未匹配={}, 总用户={}, 错误={}",
                    result.matched(), result.unmatched(), result.totalTmUsers(), result.errors());
        } catch (Exception e) {
            log.error("[TencentMeetingSync] 启动同步异常", e);
        }
    }

    /**
     * 同步结果
     */
    public record SyncResult(int matched, int unmatched, int totalTmUsers, List<String> errors) {}

    /**
     * 执行同步：拉取腾讯会议用户列表，按手机号匹配 ERP 用户表，自动建立映射
     */
    public SyncResult syncUsers() {
        // 1. 获取操作者 ID（从已有映射中取第一个）
        String operatorId = resolveOperatorId();
        if (operatorId == null) {
            return new SyncResult(0, 0, 0,
                    List.of("未找到操作者ID，请先在映射表中配置 admin 用户的 tencent_user_id（如 wemeeting8151462）"));
        }

        int totalTmUsers = 0;
        int matched = 0;
        int unmatched = 0;
        List<String> errors = new ArrayList<>();
        String pos = "";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        try {
            do {
                // 2. 构建查询参数
                String queryString = "operator_id=" + urlEncode(operatorId)
                        + "&operator_id_type=1"
                        + "&size=" + PAGE_SIZE;
                if (!pos.isEmpty()) {
                    queryString += "&pos=" + urlEncode(pos);
                }

                String requestUri = "/v1/users/advance/list";
                String fullUrl = requestUri + "?" + queryString;

                // 3. 生成签名（GET 请求的 URI 必须包含查询参数）
                Map<String, String> headers = signatureService.generateCommonHeaders();
                String signature = signatureService.calculateSignature("GET", fullUrl, "", headers);

                // 4. 发起 GET 请求
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

                HttpRequest request = requestBuilder.GET().build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    errors.add("拉取用户列表失败: HTTP " + response.statusCode() + " - " + response.body());
                    break;
                }

                // 5. 解析响应
                JsonNode root = MAPPER.readTree(response.body());
                JsonNode usersNode = root.get("users");
                if (usersNode != null && usersNode.isArray()) {
                    for (JsonNode userNode : usersNode) {
                        totalTmUsers++;
                        String tmUserId = userNode.path("userid").asText();
                        String tmUserName = userNode.path("username").asText("");
                        String phone = userNode.path("phone").asText(null);

                        if (phone == null || phone.isBlank()) {
                            unmatched++;
                            continue;
                        }

                        // 清理手机号：去除 +86 前缀、空格、横线等
                        String cleanPhone = cleanPhoneNumber(phone);

                        // 6. 直接按手机号查 ERP 用户表
                        Optional<User> userOpt = userRepository.findByPhone(cleanPhone);
                        if (userOpt.isPresent()) {
                            String erpUserId = userOpt.get().getUserId();
                            // 7. 写入/更新映射
                            saveMapping(erpUserId, tmUserId, tmUserName, cleanPhone);
                            matched++;
                            log.info("[TencentMeetingSync] 匹配成功: ERP={} → TM={} (手机号={})", erpUserId, tmUserId, cleanPhone);
                        } else {
                            unmatched++;
                            log.warn("[TencentMeetingSync] 未匹配到ERP用户: TM={}, phone={}", tmUserId, cleanPhone);
                        }
                    }
                }

                // 8. 分页处理
                boolean hasRemaining = root.path("has_remaining").asBoolean(false);
                pos = hasRemaining ? root.path("next_pos").asText("") : "";

            } while (!pos.isEmpty());

        } catch (Exception e) {
            log.error("[TencentMeetingSync] 同步异常", e);
            errors.add("同步异常: " + e.getMessage());
        }

        log.info("[TencentMeetingSync] 同步完成: 腾讯会议用户={}, 匹配成功={}, 未匹配={}, 错误={}",
                totalTmUsers, matched, unmatched, errors.size());
        return new SyncResult(matched, unmatched, totalTmUsers, errors);
    }

    /**
     * 获取用于调用 API 的操作者 ID（Tencent Meeting userid）
     */
    private String resolveOperatorId() {
        return mappingRepository.findAll().stream()
                .findFirst()
                .map(TencentUserMapping::getTencentUserId)
                .orElse(null);
    }

    /**
     * 清理手机号：去除 +86 前缀、空格、横线
     */
    private String cleanPhoneNumber(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("[\\s\\-]", "")
                .replaceAll("^\\+86", "");
    }

    /**
     * 写入或更新映射
     */
    private void saveMapping(String erpUserId, String tmUserId, String tmUserName, String phone) {
        Optional<TencentUserMapping> existing = mappingRepository.findByErpUserId(erpUserId);
        if (existing.isPresent()) {
            TencentUserMapping mapping = existing.get();
            mapping.setTencentUserId(tmUserId);
            mapping.setTencentUsername(tmUserName);
            mapping.setPhone(phone);
            mapping.setUpdatedAt(Instant.now());
            mappingRepository.save(mapping);
        } else {
            TencentUserMapping mapping = TencentUserMapping.builder()
                    .erpUserId(erpUserId)
                    .tencentUserId(tmUserId)
                    .tencentUsername(tmUserName)
                    .phone(phone)
                    .remark("通过手机号自动同步")
                    .build();
            mappingRepository.save(mapping);
        }
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
                    // 优先显示腾讯会议用户名，没有则用 ERP 用户名兜底
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
     * 直接从腾讯会议拉取所有用户列表（用于创建会议时选择参会人）
     */
    public List<Map<String, String>> listAllTmUsers() {
        String operatorId = resolveOperatorId();
        if (operatorId == null) return List.of();

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
