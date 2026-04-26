# 钉钉考勤打卡数据接入实现计划

**目标：** 将钉钉考勤打卡数据自动拉取并持久化，在 ERP 系统中支持独立考勤工资页面、个人中心查看、纠偏调整机制。

**架构：** 后端新增 Spring Service + JPA Entity 体系，调用钉钉考勤 API，结果持久化到 PostgreSQL；前端新增 FinanceAttendanceView 页面，管理员可查看全员考勤记录，员工可在个人中心查看自己的考勤数据。

**技术栈：** JDK HttpClient（Java 11+）、Spring @Scheduled、Spring Data JPA、PostgreSQL、Vue 3 + Pinia + Axios

---

## 文件结构总览

### 后端新建

| 文件 | 职责 |
|------|------|
| `entity/AttendanceRecord.java` | 打卡记录实体 |
| `entity/AttendanceAdjustment.java` | 纠偏记录实体 |
| `repository/AttendanceRecordRepository.java` | 打卡数据访问 |
| `repository/AttendanceAdjustmentRepository.java` | 纠偏记录访问 |
| `service/DingTalkTokenService.java` | access_token 获取与缓存 |
| `service/DingTalkAttendanceService.java` | 打卡数据拉取核心逻辑 |
| `service/AttendanceService.java` | 本地考勤查询与纠偏 |
| `scheduler/DingTalkAttendanceScheduler.java` | 每天凌晨 2 点定时任务 |
| `controller/AttendanceController.java` | REST API（拉取/查询/纠偏） |

### 前端新建

| 文件 | 职责 |
|------|------|
| `views/finance/FinanceAttendanceView.vue` | 考勤工资独立页面 |
| `stores/attendanceStore.js` | 考勤数据 Pinia Store |

### 配置修改

| 文件 | 修改内容 |
|------|---------|
| `erp-backend/.env` | 新增 DINGTALK_APP_KEY / DINGTALK_APP_SECRET |
| `erp-backend/src/main/resources/application.yml` | 新增 dingtalk 配置块 |
| `lab-erp-demo/src/views/finance/FinanceOverviewView.vue` | 新增考勤工资入口卡片 |
| `lab-erp-demo/src/router/index.js` | 新增路由 /finance/attendance |

---

## Task 1: 数据库实体 — AttendanceRecord

**文件:**
- 创建: `erp-backend/src/main/java/com/smartlab/erp/entity/AttendanceRecord.java`

```java
package com.smartlab.erp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_record",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "user_check_time", "check_type"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "user_name", length = 128)
    private String userName;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_type", length = 32)
    private String checkType; // OnDuty / OffDuty

    @Column(name = "source_type", length = 32)
    private String sourceType; // USER / ATM / BEACON 等

    @Column(name = "time_result", length = 32)
    private String timeResult; // Normal / Late / Early 等

    @Column(name = "location_result", length = 32)
    private String locationResult; // Normal / Outside 等

    @Column(name = "is_legal", length = 8)
    private String isLegal; // Y / N

    @Column(name = "user_address", length = 256)
    private String userAddress;

    @Column(name = "user_longitude", length = 32)
    private String userLongitude;

    @Column(name = "user_latitude", length = 32)
    private String userLatitude;

    @Column(name = "base_address", length = 256)
    private String baseAddress;

    @Column(name = "user_check_time", nullable = false)
    private Instant userCheckTime;

    @Column(name = "plan_check_time")
    private Instant planCheckTime;

    @Column(name = "corp_id", length = 64)
    private String corpId;

    @Column(name = "group_id", length = 64)
    private String groupId;

    @Column(name = "class_id", length = 64)
    private String classId;

    @Column(name = "invalid_record_type", length = 64)
    private String invalidRecordType;

    @Column(name = "invalid_record_msg", length = 256)
    private String invalidRecordMsg;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "remark", length = 256)
    private String remark;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
```

- [ ] 创建文件 `AttendanceRecord.java`
- [ ] 执行数据库建表（Flyway 或手动 SQL）：
```sql
CREATE TABLE attendance_record (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             VARCHAR(64) NOT NULL,
    user_name           VARCHAR(128),
    work_date           DATE NOT NULL,
    check_type          VARCHAR(32),
    source_type         VARCHAR(32),
    time_result         VARCHAR(32),
    location_result     VARCHAR(32),
    is_legal            VARCHAR(8),
    user_address        VARCHAR(256),
    user_longitude      VARCHAR(32),
    user_latitude       VARCHAR(32),
    base_address        VARCHAR(256),
    user_check_time     TIMESTAMP NOT NULL,
    plan_check_time     TIMESTAMP,
    corp_id             VARCHAR(64),
    group_id            VARCHAR(64),
    class_id            VARCHAR(64),
    invalid_record_type VARCHAR(64),
    invalid_record_msg  VARCHAR(256),
    device_id           VARCHAR(128),
    remark              VARCHAR(256),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, user_check_time, check_type)
);
CREATE INDEX idx_attendance_user_date ON attendance_record(user_id, work_date);
```

---

## Task 2: 数据库实体 — AttendanceAdjustment

**文件:**
- 创建: `erp-backend/src/main/java/com/smartlab/erp/entity/AttendanceAdjustment.java`

```java
package com.smartlab.erp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "attendance_adjustment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "adjust_date", nullable = false)
    private LocalDate adjustDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "original_data", columnDefinition = "jsonb")
    private Map<String, Object> originalData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adjusted_data", columnDefinition = "jsonb")
    private Map<String, Object> adjustedData;

    @Column(name = "reason", nullable = false, length = 256)
    private String reason;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
```

- [ ] 创建文件 `AttendanceAdjustment.java`
- [ ] 执行数据库建表：
```sql
CREATE TABLE attendance_adjustment (
    id                BIGSERIAL PRIMARY KEY,
    user_id          VARCHAR(64) NOT NULL,
    adjust_date      DATE NOT NULL,
    original_data    JSONB,
    adjusted_data    JSONB,
    reason           VARCHAR(256) NOT NULL,
    operator_id      VARCHAR(64) NOT NULL,
    operator_name    VARCHAR(128),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_adjustment_user_date ON attendance_adjustment(user_id, adjust_date);
```

---

## Task 3: Repository 层

**文件:**
- 创建: `erp-backend/src/main/java/com/smartlab/erp/repository/AttendanceRecordRepository.java`
- 创建: `erp-backend/src/main/java/com/smartlab/erp/repository/AttendanceAdjustmentRepository.java`

`AttendanceRecordRepository.java`:
```java
package com.smartlab.erp.repository;

import com.smartlab.erp.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByUserIdAndWorkDateOrderByUserCheckTimeAsc(String userId, LocalDate workDate);

    List<AttendanceRecord> findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(LocalDate from, LocalDate to);

    List<AttendanceRecord> findByUserIdOrderByWorkDateDesc(String userId);

    boolean existsByUserIdAndUserCheckTimeAndCheckType(String userId, java.time.Instant userCheckTime, String checkType);

    Optional<AttendanceRecord> findByUserIdAndWorkDateAndCheckType(String userId, LocalDate workDate, String checkType);
}
```

`AttendanceAdjustmentRepository.java`:
```java
package com.smartlab.erp.repository;

import com.smartlab.erp.entity.AttendanceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceAdjustmentRepository extends JpaRepository<AttendanceAdjustment, Long> {

    List<AttendanceAdjustment> findByUserIdAndAdjustDate(String userId, LocalDate adjustDate);

    List<AttendanceAdjustment> findByUserIdOrderByCreatedAtDesc(String userId);
}
```

---

## Task 4: DingTalkTokenService

**文件:**
- 创建: `erp-backend/src/main/java/com/smartlab/erp/service/DingTalkTokenService.java`

核心逻辑：
- 使用 JDK `HttpClient` 调钉钉 `POST /v1.0/oauth2/accessToken`
- 内存缓存 token，剩余有效期 < 5 分钟时自动刷新
- 配置项：`dingtalk.app-key` / `dingtalk.app-secret`

```java
package com.smartlab.erp.service;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
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

    public String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minus(TOKEN_EXPIRE_BUFFER))) {
            return cachedToken;
        }
        refreshToken();
        return cachedToken;
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
            if (response.statusCode() == 200) {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
                cachedToken = node.get("accessToken").asText();
                // token 有效期 2 小时（7200s），提前 5 分钟刷新
                tokenExpiresAt = Instant.now().plusSeconds(7200);
                log.info("[DingTalk] Token refreshed successfully, expires at {}", tokenExpiresAt);
            } else {
                throw new RuntimeException("Failed to refresh DingTalk token: " + response.body());
            }
        } catch (Exception e) {
            log.error("[DingTalk] Token refresh failed", e);
            throw new RuntimeException("DingTalk token refresh failed", e);
        }
    }
}
```

---

## Task 5: DingTalkAttendanceService

**文件:**
- 创建: `erp-backend/src/main/java/com/smartlab/erp/service/DingTalkAttendanceService.java`

核心逻辑：
- `pullAttendance(userIds, dateFrom, dateTo)` — 调 `POST /attendance/listRecord`
- `fetchAllDingTalkUsers()` — 调 `POST /user/listId` 获取全员 userId 列表
- 解析返回的 `recordresult`，写入 `AttendanceRecord`
- 唯一键防重：数据库 `UNIQUE(user_id, user_check_time, check_type)`

```java
package com.smartlab.erp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.entity.AttendanceRecord;
import com.smartlab.erp.repository.AttendanceRecordRepository;
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

    private final DingTalkTokenService tokenService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private static final String LIST_RECORD_URL = "https://oapi.dingtalk.com/attendance/listRecord";
    private static final String LIST_USER_URL = "https://oapi.dingtalk.com/topapi/user/listid";
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void pullAttendance(List<String> userIds, LocalDate dateFrom, LocalDate dateTo) {
        String token = tokenService.getAccessToken();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userIds", userIds);
            payload.put("checkDateFrom", dateFrom.atStartOfDay().format(DT_FORMATTER));
            payload.put("checkDateTo", dateTo.atTime(23, 59, 59).format(DT_FORMATTER));
            payload.put("isI18n", false);

            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LIST_RECORD_URL + "?access_token=" + token))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            int errcode = root.has("errcode") ? root.get("errcode").asInt() : 0;
            if (errcode != 0) {
                log.error("[DingTalk] pullAttendance failed: {}", root);
                return;
            }

            JsonNode records = root.get("recordresult");
            if (records == null || !records.isArray()) return;

            int saved = 0;
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
            log.info("[DingTalk] pullAttendance saved {}/{} new records for {} users, date {} ~ {}",
                    saved, records.size(), userIds.size(), dateFrom, dateTo);
        } catch (Exception e) {
            log.error("[DingTalk] pullAttendance failed", e);
        }
    }

    public List<String> fetchAllDingTalkUsers() {
        String token = tokenService.getAccessToken();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = Map.of("dept_ids", List.of(1)); // 根部门
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LIST_USER_URL + "?access_token=" + token))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            List<String> userIds = new ArrayList<>();
            if (root.has("result") && root.get("result").has("userid_list")) {
                for (JsonNode uid : root.get("result").get("userid_list")) {
                    userIds.add(uid.asText());
                }
            }
            log.info("[DingTalk] fetchAllDingTalkUsers: {} users", userIds.size());
            return userIds;
        } catch (Exception e) {
            log.error("[DingTalk] fetchAllDingTalkUsers failed", e);
            return List.of();
        }
    }

    private AttendanceRecord parseRecord(JsonNode r) {
        String userId = safeText(r, "userId");
        long userCheckTimeMs = r.has("userCheckTime") ? r.get("userCheckTime").asLong() : 0;
        Instant userCheckTime = Instant.ofEpochMilli(userCheckTimeMs);
        LocalDate workDate = LocalDate.ofInstant(userCheckTime, ZoneId.systemDefault());

        return AttendanceRecord.builder()
                .userId(userId)
                .userName("")
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
                .planCheckTime(r.has("planCheckTime") ? Instant.ofEpochMilli(r.get("planCheckTime").asLong()) : null)
                .corpId(safeText(r, "corpId"))
                .groupId(r.has("groupId") ? r.get("groupId").asText() : null)
                .classId(r.has("classId") ? r.get("classId").asText() : null)
                .invalidRecordType(safeText(r, "invalidRecordType"))
                .invalidRecordMsg(safeText(r, "invalidRecordMsg"))
                .deviceId(safeText(r, "deviceId"))
                .remark(safeText(r, "outsideRemark"))
                .build();
    }

    private String safeText(JsonNode n, String field) {
        return n.has(field) && !n.get(field).isNull() ? n.get(field).asText() : null;
    }
}
```

---

## Task 6: AttendanceService

**文件:**
- 创建: `erp-backend/src/main/java/com/smartlab/erp/service/AttendanceService.java`

职责：
- 查询某人某日期范围的打卡记录
- 提交纠偏申请
- 管理员审批纠偏

```java
package com.smartlab.erp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.entity.AttendanceAdjustment;
import com.smartlab.erp.entity.AttendanceRecord;
import com.smartlab.erp.repository.AttendanceAdjustmentRepository;
import com.smartlab.erp.repository.AttendanceRecordRepository;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.util.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceAdjustmentRepository adjustmentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<AttendanceRecord> getUserAttendance(String userId, LocalDate from, LocalDate to) {
        return recordRepository.findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(from, to)
                .stream().filter(r -> r.getUserId().equals(userId)).toList();
    }

    public List<AttendanceRecord> getAllAttendance(LocalDate from, LocalDate to) {
        return recordRepository.findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(from, to);
    }

    public Map<String, Object> submitAdjustment(String userId, LocalDate adjustDate,
                                                 Map<String, Object> originalData,
                                                 Map<String, Object> adjustedData,
                                                 String reason) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        AttendanceAdjustment adj = AttendanceAdjustment.builder()
                .userId(userId)
                .adjustDate(adjustDate)
                .originalData(originalData)
                .adjustedData(adjustedData)
                .reason(reason)
                .operatorId(currentUser != null ? currentUser.getId() : "system")
                .operatorName(currentUser != null ? currentUser.getName() : "system")
                .build();
        adjustmentRepository.save(adj);
        // 更新原记录
        List<AttendanceRecord> existing = recordRepository.findByUserIdAndWorkDateOrderByUserCheckTimeAsc(userId, adjustDate);
        for (AttendanceRecord rec : existing) {
            if (adjustedData.containsKey("userCheckTime")) {
                long newTime = ((Number) adjustedData.get("userCheckTime")).longValue();
                rec.setUserCheckTime(java.time.Instant.ofEpochMilli(newTime));
            }
            if (adjustedData.containsKey("timeResult")) {
                rec.setTimeResult((String) adjustedData.get("timeResult"));
            }
            if (adjustedData.containsKey("isLegal")) {
                rec.setIsLegal((String) adjustedData.get("isLegal"));
            }
            recordRepository.save(rec);
        }
        log.info("[Attendance] Adjustment submitted for user {} on {} by {}", userId, adjustDate, currentUser != null ? currentUser.getId() : "system");
        return Map.of("success", true, "adjustmentId", adj.getId());
    }

    public List<AttendanceAdjustment> getAdjustments(String userId) {
        return adjustmentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
```

---

## Task 7: DingTalkAttendanceScheduler

**文件:**
- 创建: `erp-backend/src/main/java/com/smartlab/erp/scheduler/DingTalkAttendanceScheduler.java`

```java
package com.smartlab.erp.scheduler;

import com.smartlab.erp.service.DingTalkAttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DingTalkAttendanceScheduler {

    private final DingTalkAttendanceService attendanceService;
    private static final LocalDate HISTORY_START = LocalDate.of(2026, 4, 1);

    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨 2:00
    public void pullYesterdayAttendance() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[Scheduler] Starting daily attendance pull for {}", yesterday);
        try {
            List<String> allUsers = attendanceService.fetchAllDingTalkUsers();
            // 分页：每次最多 50 人
            for (int i = 0; i < allUsers.size(); i += 50) {
                List<String> batch = allUsers.subList(i, Math.min(i + 50, allUsers.size()));
                attendanceService.pullAttendance(batch, yesterday, yesterday);
            }
            log.info("[Scheduler] Daily attendance pull completed for {}", yesterday);
        } catch (Exception e) {
            log.error("[Scheduler] Daily attendance pull failed", e);
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE) // 启动后 1 分钟执行一次
    public void pullHistoricalAttendance() {
        log.info("[Scheduler] Starting historical attendance pull from {} to yesterday", HISTORY_START);
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate cursor = HISTORY_START;
        int batchSize = 7;
        int totalPulled = 0;
        while (!cursor.isAfter(end)) {
            LocalDate batchEnd = cursor.plusDays(batchSize - 1);
            if (batchEnd.isAfter(end)) batchEnd = end;
            try {
                List<String> allUsers = attendanceService.fetchAllDingTalkUsers();
                for (int i = 0; i < allUsers.size(); i += 50) {
                    List<String> batch = allUsers.subList(i, Math.min(i + 50, allUsers.size()));
                    attendanceService.pullAttendance(batch, cursor, batchEnd);
                }
                totalPulled++;
                log.info("[Scheduler] Historical batch pulled: {} ~ {}, batch #{}/{}",
                        cursor, batchEnd, totalPulled, (end.toEpochDay() - HISTORY_START.toEpochDay()) / batchSize + 1);
            } catch (Exception e) {
                log.error("[Scheduler] Historical batch failed for {} ~ {}", cursor, batchEnd, e);
            }
            cursor = batchEnd.plusDays(1);
        }
        log.info("[Scheduler] Historical attendance pull completed. Total batches: {}", totalPulled);
    }
}
```

---

## Task 8: AttendanceController

**文件:**
- 创建: `erp-backend/src/main/java/com/smartlab/erp/controller/AttendanceController.java`

```java
package com.smartlab.erp.controller;

import com.smartlab.erp.entity.AttendanceAdjustment;
import com.smartlab.erp.entity.AttendanceRecord;
import com.smartlab.erp.service.AttendanceService;
import com.smartlab.erp.service.DingTalkAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final DingTalkAttendanceService dingTalkService;

    @GetMapping("/records")
    public ResponseEntity<List<AttendanceRecord>> getRecords(
            @RequestParam(required = false) String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (userId != null && !userId.isBlank()) {
            return ResponseEntity.ok(attendanceService.getUserAttendance(userId, from, to));
        }
        return ResponseEntity.ok(attendanceService.getAllAttendance(from, to));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<Map<String, Object>> submitAdjustment(
            @RequestBody Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        LocalDate adjustDate = LocalDate.parse((String) payload.get("adjustDate"));
        @SuppressWarnings("unchecked")
        Map<String, Object> originalData = (Map<String, Object>) payload.get("originalData");
        @SuppressWarnings("unchecked")
        Map<String, Object> adjustedData = (Map<String, Object>) payload.get("adjustedData");
        String reason = (String) payload.get("reason");
        return ResponseEntity.ok(attendanceService.submitAdjustment(userId, adjustDate, originalData, adjustedData, reason));
    }

    @GetMapping("/adjustments")
    public ResponseEntity<List<AttendanceAdjustment>> getAdjustments(@RequestParam String userId) {
        return ResponseEntity.ok(attendanceService.getAdjustments(userId));
    }

    @PostMapping("/pull")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_system')")
    public ResponseEntity<Void> triggerPull(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<String> users = dingTalkService.fetchAllDingTalkUsers();
        for (int i = 0; i < users.size(); i += 50) {
            dingTalkService.pullAttendance(users.subList(i, Math.min(i + 50, users.size())), from, to);
        }
        return ResponseEntity.ok().build();
    }
}
```

---

## Task 9: 配置

**文件:**
- 修改: `erp-backend/.env`（在 `.env` 文件末尾添加）
- 修改: `erp-backend/src/main/resources/application.yml`

`.env` 新增：
```env
DINGTALK_APP_KEY=dingm2lxsdzevjjtemw7
DINGTALK_APP_SECRET=gQddYkOFZM7lgmHfuje8utNKaS3lB39Iu031v73KrCq5v64QxtS54RdpOQXO_LkG
```

`application.yml` 新增：
```yaml
dingtalk:
  app-key: ${DINGTALK_APP_KEY}
  app-secret: ${DINGTALK_APP_SECRET}
```

---

## Task 10: 前端 — FinanceAttendanceView.vue

**文件:**
- 创建: `lab-erp-demo/src/views/finance/FinanceAttendanceView.vue`

页面结构：
- 顶部：日期范围选择器 + 人员筛选下拉框 + 手动刷新按钮
- 主体：el-table 展示打卡记录（分页）
  - 列：姓名 / 日期 / 上班打卡时间 / 下班打卡时间 / 上班打卡结果 / 下班打卡结果 / 是否合法 / 打卡地点 / 操作（申请修正）
- 弹窗：申请修正 — 填写调整原因 + 正确打卡时间
- 月度汇总卡片：每人本月出勤天数 / 迟到次数 / 早退次数 / 异常次数

---

## Task 11: 前端 — attendanceStore.js

**文件:**
- 创建: `lab-erp-demo/src/stores/attendanceStore.js`

```javascript
import { defineStore } from 'pinia'
import request from '@/utils/request'

export const useAttendanceStore = defineStore('attendance', {
  state: () => ({
    records: [],
    adjustments: [],
    loading: false,
    dateRange: [new Date(Date.now() - 7 * 86400000), new Date()],
    selectedUserId: ''
  }),

  actions: {
    async fetchRecords(params = {}) {
      this.loading = true
      try {
        const res = await request.get('/api/attendance/records', { params })
        this.records = res.data || res || []
      } finally {
        this.loading = false
      }
    },

    async submitAdjustment(payload) {
      const res = await request.post('/api/attendance/adjustments', payload)
      return res
    },

    async fetchAdjustments(userId) {
      const res = await request.get('/api/attendance/adjustments', { params: { userId } })
      this.adjustments = res.data || res || []
    }
  }
})
```

---

## Task 12: 前端 — FinanceOverviewView.vue 路由入口

**文件:**
- 修改: `lab-erp-demo/src/router/index.js`（新增路由）
- 修改: `lab-erp-demo/src/views/finance/FinanceOverviewView.vue`（新增考勤工资入口卡片）

在 FinanceOverviewView 的三列布局中，参考现有产品/科研入口，新增一个 "考勤工资" 入口卡片：
- 路由：`/finance/attendance`
- 图标：🕐
- 标题：考勤工资
- 描述：钉钉考勤打卡数据管理

---

## Task 13: 个人中心考勤模块（位置待确认）

**文件:** （路径待定，需先确认个人中心组件路径）

在个人中心页面新增考勤月历视图：
- 月历格子，每天显示：上班打卡时间、下班打卡时间、是否正常
- 有异常的日期高亮
- 点击日期查看详情
- 纠偏申请按钮

（待用户确认个人中心组件路径后补充实现细节）

---

## Task 14: 构建与发布

- [ ] 后端：构建 Docker 镜像 tag `v1.68`，推送到 `127.0.0.1:5555/zhangqi_backend:v1.68`，重启容器
- [ ] 前端：构建 Docker 镜像 tag `v1.102`，推送到 `127.0.0.1:5555/zhangqi_frontend:v1.102`，重启容器
- [ ] 验证：登录 ERP，Finance Overview 出现"考勤工资"入口，点击进入查看打卡数据
