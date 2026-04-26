package com.smartlab.erp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.entity.AttendanceRecord;
import com.smartlab.erp.entity.DingTalkUserDirectory;
import com.smartlab.erp.exception.DingTalkIntegrationException;
import com.smartlab.erp.repository.AttendanceRecordRepository;
import com.smartlab.erp.repository.DingTalkUserDirectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DingTalkAttendanceService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int PAGE_SIZE = 50;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DingTalkTokenService tokenService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final DingTalkUserDirectoryRepository dingTalkUserDirectoryRepository;
    private final DingTalkUserDirectoryService dingTalkUserDirectoryService;
    private static final String LIST_RECORD_URL = "https://oapi.dingtalk.com/attendance/list";
    private static final String LIST_USER_URL = "https://oapi.dingtalk.com/topapi/v2/user/list";
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void pullAttendance(List<String> userIds, LocalDate dateFrom, LocalDate dateTo) {
        if (userIds == null || userIds.isEmpty() || dateFrom == null || dateTo == null) {
            return;
        }

        try {
            LocalDateTime workDateFrom = dateFrom.atStartOfDay();
            LocalDateTime workDateTo = resolveWorkDateTo(dateTo);

            int offset = 0;
            int saved = 0;
            int fetched = 0;
            while (true) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("workDateFrom", workDateFrom.format(DT_FORMATTER));
                payload.put("workDateTo", workDateTo.format(DT_FORMATTER));
                payload.put("userIdList", userIds);
                payload.put("offset", offset);
                payload.put("limit", PAGE_SIZE);
                payload.put("isI18n", false);

                JsonNode root = invokeDingTalkJson("拉取考勤记录", LIST_RECORD_URL, payload);
                JsonNode records = root.path("recordresult");
                if (records == null || !records.isArray() || records.isEmpty()) {
                    break;
                }

                fetched += records.size();
                for (JsonNode r : records) {
                    try {
                        AttendanceRecord record = parseRecord(r);
                        boolean exists = attendanceRecordRepository.existsByUserIdAndUserCheckTimeAndCheckType(
                                record.getUserId(), record.getUserCheckTime(), record.getCheckType());
                        if (!exists) {
                            attendanceRecordRepository.save(record);
                            saved++;
                        }
                    } catch (Exception e) {
                        log.warn("[DingTalk] Failed to parse record: {}", r, e);
                    }
                }

                boolean hasMore = root.path("hasMore").asBoolean(false) || root.path("has_more").asBoolean(false);
                if (!hasMore || records.size() < PAGE_SIZE) {
                    break;
                }
                offset += PAGE_SIZE;
            }

            log.info("[DingTalk] pullAttendance saved {}/{} new records for {} DingTalk users, workDate {} ~ {}",
                    saved, fetched, userIds.size(), workDateFrom, workDateTo);
        } catch (Exception e) {
            if (e instanceof DingTalkIntegrationException dingTalkIntegrationException) {
                throw dingTalkIntegrationException;
            }
            throw new DingTalkIntegrationException("拉取考勤记录", "拉取考勤记录失败: " + e.getMessage(), null, null, e);
        }
    }

    public List<String> fetchAllDingTalkUsers() {
        try {
            List<String> userIds = new ArrayList<>();
            long cursor = 0L;
            boolean hasMore = true;

            while (hasMore) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("dept_id", 1);
                payload.put("cursor", cursor);
                payload.put("size", PAGE_SIZE);

                JsonNode root = invokeDingTalkJson("拉取钉钉用户列表", LIST_USER_URL, payload);
                JsonNode result = root.path("result");
                JsonNode list = result.path("list");
                if (list == null || !list.isArray()) {
                    throw new DingTalkIntegrationException("拉取钉钉用户列表", "钉钉用户列表返回结构异常: 缺少 result.list", null, root.toString());
                }

                for (JsonNode user : list) {
                    String userId = safeText(user, "userid");
                    if (userId != null && !userId.isBlank()) {
                        userIds.add(userId);
                        dingTalkUserDirectoryRepository.save(DingTalkUserDirectory.builder()
                                .userId(userId)
                                .name(safeText(user, "name"))
                                .mobile(safeText(user, "mobile"))
                                .deptIdsJson(user.path("dept_id_list").isMissingNode() ? null : user.path("dept_id_list").toString())
                                .active(user.has("active") ? user.get("active").asBoolean() : null)
                                .admin(user.has("admin") ? user.get("admin").asBoolean() : null)
                                .avatar(safeText(user, "avatar"))
                                .title(safeText(user, "title"))
                                .build());
                    }
                }

                hasMore = result.path("has_more").asBoolean(false);
                if (hasMore) {
                    JsonNode nextCursorNode = result.get("next_cursor");
                    if (nextCursorNode == null || nextCursorNode.isNull()) {
                        throw new DingTalkIntegrationException("拉取钉钉用户列表", "钉钉用户列表返回了 has_more=true，但没有 next_cursor", null, root.toString());
                    }
                    cursor = nextCursorNode.isNumber() ? nextCursorNode.asLong() : Long.parseLong(nextCursorNode.asText());
                }
            }
            log.info("[DingTalk] fetchAllDingTalkUsers: {} DingTalk users", userIds.size());
            int backfilled = dingTalkUserDirectoryService.backfillAttendanceNames();
            if (backfilled > 0) {
                log.info("[DingTalk] Backfilled {} attendance records with cached names", backfilled);
            }
            return userIds;
        } catch (Exception e) {
            if (e instanceof DingTalkIntegrationException dingTalkIntegrationException) {
                throw dingTalkIntegrationException;
            }
            throw new DingTalkIntegrationException("拉取钉钉用户列表", "拉取钉钉用户列表失败: " + e.getMessage(), null, null, e);
        }
    }

    private JsonNode invokeDingTalkJson(String stage, String url, Map<String, Object> payload) {
        return invokeDingTalkJson(stage, url, payload, true);
    }

    private JsonNode invokeDingTalkJson(String stage, String url, Map<String, Object> payload, boolean retryAllowed) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            String token = tokenService.getAccessToken();
            String body = MAPPER.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "?access_token=" + token))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(response.body());

            if (isAuthFailure(response, root)) {
                if (retryAllowed) {
                    log.warn("[DingTalk] {} detected auth failure, refreshing token and retrying once", stage);
                    tokenService.invalidateToken();
                    tokenService.refreshAccessToken();
                    return invokeDingTalkJson(stage, url, payload, false);
                }
                throw new DingTalkIntegrationException(stage, formatDingTalkError(stage, response.body(), root), extractErrCode(root), response.body());
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DingTalkIntegrationException(stage, formatDingTalkError(stage, response.body(), root), extractErrCode(root), response.body());
            }

            int errcode = extractErrCode(root);
            if (errcode != 0) {
                if (retryAllowed && isAuthErrCode(errcode)) {
                    log.warn("[DingTalk] {} returned auth errcode {}, refreshing token and retrying once", stage, errcode);
                    tokenService.invalidateToken();
                    tokenService.refreshAccessToken();
                    return invokeDingTalkJson(stage, url, payload, false);
                }
                throw new DingTalkIntegrationException(stage, formatDingTalkError(stage, response.body(), root), errcode, response.body());
            }

            return root;
        } catch (DingTalkIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new DingTalkIntegrationException(stage, stage + "失败: " + e.getMessage(), null, null, e);
        }
    }

    private boolean isAuthFailure(HttpResponse<String> response, JsonNode root) {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            return true;
        }
        return isAuthErrCode(extractErrCode(root));
    }

    private boolean isAuthErrCode(int errcode) {
        return errcode == 40014 || errcode == 42001 || errcode == 40001;
    }

    private int extractErrCode(JsonNode root) {
        return root != null && root.has("errcode") ? root.get("errcode").asInt() : 0;
    }

    private String formatDingTalkError(String stage, String responseBody, JsonNode root) {
        String errmsg = root != null && root.has("errmsg") ? root.get("errmsg").asText() : null;
        if (errmsg != null && !errmsg.isBlank()) {
            return stage + "失败: " + errmsg;
        }
        if (responseBody != null && !responseBody.isBlank()) {
            return stage + "失败: " + responseBody;
        }
        return stage + "失败";
    }

    private AttendanceRecord parseRecord(JsonNode r) {
        String userId = safeText(r, "userId");
        if (userId == null) {
            userId = safeText(r, "userid");
        }
        long userCheckTimeMs = r.has("userCheckTime") ? r.get("userCheckTime").asLong() : 0;
        Instant userCheckTime = Instant.ofEpochMilli(userCheckTimeMs);
        LocalDate workDate = LocalDate.ofInstant(userCheckTime, SHANGHAI_ZONE);

        return AttendanceRecord.builder()
                .userId(userId)
                .userName(dingTalkUserDirectoryService.resolveUserName(userId))
                .workDate(workDate)
                .checkType(safeText(r, "checkType"))
                .sourceType(safeText(r, "sourceType"))
                .timeResult(safeText(r, "timeResult"))
                .locationResult(safeText(r, "locationResult"))
                .isLegal(safeText(r, "isLegal"))
                .userAddress(safeText(r, "userAddress"))
                .userLongitude(r.has("userLongitude") ? r.get("userLongitude").asText() : null)
                .userLatitude(r.has("userLatitude") ? r.get("userLatitude").asText() : null)
                .baseAddress(safeText(r, "baseAddress"))
                .userCheckTime(userCheckTime)
                .planCheckTime(r.has("planCheckTime") && !r.get("planCheckTime").isNull() ? Instant.ofEpochMilli(r.get("planCheckTime").asLong()) : null)
                .corpId(safeText(r, "corpId"))
                .groupId(r.has("groupId") && !r.get("groupId").isNull() ? r.get("groupId").asText() : null)
                .classId(r.has("classId") && !r.get("classId").isNull() ? r.get("classId").asText() : null)
                .invalidRecordType(safeText(r, "invalidRecordType"))
                .invalidRecordMsg(safeText(r, "invalidRecordMsg"))
                .deviceId(safeText(r, "deviceId"))
                .remark(safeText(r, "outsideRemark"))
                .build();
    }

    private String safeText(JsonNode n, String field) {
        return n.has(field) && !n.get(field).isNull() ? n.get(field).asText() : null;
    }

    private LocalDateTime resolveWorkDateTo(LocalDate dateTo) {
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        if (dateTo.isEqual(today)) {
            return LocalDateTime.now(SHANGHAI_ZONE).withSecond(0).withNano(0);
        }
        return dateTo.atTime(23, 59, 59);
    }
}
