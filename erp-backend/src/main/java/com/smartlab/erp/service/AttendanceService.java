package com.smartlab.erp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.entity.AttendanceAdjustment;
import com.smartlab.erp.entity.AttendanceRecord;
import com.smartlab.erp.entity.DingTalkUserDirectory;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.repository.AttendanceAdjustmentRepository;
import com.smartlab.erp.repository.AttendanceRecordRepository;
import com.smartlab.erp.repository.DingTalkUserDirectoryRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.util.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceAdjustmentRepository adjustmentRepository;
    private final UserRepository userRepository;
    private final DingTalkUserDirectoryRepository dingTalkUserDirectoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<AttendanceRecord> getUserAttendance(String userId, LocalDate from, LocalDate to) {
        List<AttendanceRecord> records = recordRepository.findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(from, to);
        List<AttendanceRecord> directMatch = records.stream()
                .filter(r -> Objects.equals(r.getUserId(), userId))
                .toList();
        if (!directMatch.isEmpty()) {
            return directMatch;
        }

        Optional<User> systemUser = userRepository.findById(userId);
        if (systemUser.isEmpty() || systemUser.get().getName() == null || systemUser.get().getName().isBlank()) {
            return List.of();
        }

        Set<String> dingTalkUserIds = dingTalkUserDirectoryRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getName(), systemUser.get().getName()))
                .map(DingTalkUserDirectory::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (dingTalkUserIds.isEmpty()) {
            return List.of();
        }

        return records.stream()
                .filter(r -> dingTalkUserIds.contains(r.getUserId()))
                .toList();
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
        log.info("[Attendance] Adjustment submitted for user {} on {} by {}", userId, adjustDate,
                currentUser != null ? currentUser.getId() : "system");
        return Map.of("success", true, "adjustmentId", adj.getId());
    }

    public List<AttendanceAdjustment> getAdjustments(String userId) {
        return adjustmentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private static final long OVERTIME_HOURS = 11;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public record AttendanceDayRow(String userName, String userId, LocalDate workDate,
                                   LocalTime onTime, LocalTime offTime, boolean overtime,
                                   boolean notSigned) {}

    public record AttendanceUserSummary(String userName, String userId, int totalDays,
                                        int overtimeDays, int notSignedDays, double equivalentDays) {}

    public record AttendanceExportResult(byte[] csvBytes, String fileName) {}

    private boolean isNotSigned(AttendanceRecord record) {
        return record == null || "NotSigned".equals(record.getTimeResult());
    }

    public AttendanceExportResult generateAttendanceExport(LocalDate from, LocalDate to) {
        List<AttendanceRecord> allRecords = recordRepository.findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(from, to);

        Map<String, Map<LocalDate, List<AttendanceRecord>>> grouped = new LinkedHashMap<>();
        Map<String, String> userIdToName = new LinkedHashMap<>();

        for (AttendanceRecord rec : allRecords) {
            userIdToName.putIfAbsent(rec.getUserId(), rec.getUserName());
            grouped.computeIfAbsent(rec.getUserId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(rec.getWorkDate(), k -> new ArrayList<>())
                    .add(rec);
        }

        List<AttendanceDayRow> rows = new ArrayList<>();
        Map<String, AttendanceUserSummary> summaries = new LinkedHashMap<>();

        for (Map.Entry<String, Map<LocalDate, List<AttendanceRecord>>> userEntry : grouped.entrySet()) {
            String uid = userEntry.getKey();
            String uname = userIdToName.getOrDefault(uid, uid);
            int validDays = 0;
            int overtimeCount = 0;
            int notSignedCount = 0;

            for (Map.Entry<LocalDate, List<AttendanceRecord>> dayEntry : userEntry.getValue().entrySet()) {
                LocalDate date = dayEntry.getKey();
                List<AttendanceRecord> dayRecords = dayEntry.getValue();

                AttendanceRecord onDuty = dayRecords.stream().filter(r -> "OnDuty".equals(r.getCheckType())).findFirst().orElse(null);
                AttendanceRecord offDuty = dayRecords.stream().filter(r -> "OffDuty".equals(r.getCheckType())).findFirst().orElse(null);

                boolean dayNotSigned = isNotSigned(onDuty) || isNotSigned(offDuty);
                LocalTime onTime = toLocalTime(onDuty);
                LocalTime offTime = toLocalTime(offDuty);
                boolean overtime = false;

                if (!dayNotSigned) {
                    validDays++;
                    overtime = onTime != null && offTime != null && hoursBetween(onTime, offTime) > OVERTIME_HOURS;
                    if (overtime) overtimeCount++;
                } else {
                    notSignedCount++;
                }

                rows.add(new AttendanceDayRow(uname, uid, date, onTime, offTime, overtime, dayNotSigned));
            }

            double equivalent = validDays + (overtimeCount * 4.0 / 3.0);
            equivalent = Math.round(equivalent * 100.0) / 100.0;
            summaries.put(uid, new AttendanceUserSummary(uname, uid, validDays, overtimeCount, notSignedCount, equivalent));
        }

        List<DingTalkUserDirectory> allUsers = dingTalkUserDirectoryRepository.findAll();
        for (DingTalkUserDirectory dtUser : allUsers) {
            String uid = dtUser.getUserId();
            if (!summaries.containsKey(uid)) {
                String uname = dtUser.getName() != null ? dtUser.getName() : uid;
                summaries.put(uid, new AttendanceUserSummary(uname, uid, 0, 0, 0, 0.0));
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            w.write('\uFEFF');
            w.write("员工姓名,员工ID,日期,上班时间,下班时间,是否加班,打卡状态,备注\n");
            for (AttendanceDayRow row : rows) {
                String status = row.notSigned() ? "缺卡" : "正常";
                String note = "";
                if (row.notSigned()) {
                    note = "不计入出勤";
                } else if (row.overtime()) {
                    note = "加班(1又1/3天)";
                }
                w.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                        csvCell(row.userName()),
                        csvIdCell(row.userId()),
                        row.workDate().format(DATE_FMT),
                        row.onTime() != null ? row.onTime().format(TIME_FMT) : "",
                        row.offTime() != null ? row.offTime().format(TIME_FMT) : "",
                        row.overtime() ? "是" : "否",
                        status,
                        note));
            }
            w.write("\n--- 月度汇总 ---\n");
            w.write("员工姓名,员工ID,出勤天数,加班天数,缺卡天数,折算总工天\n");
            for (AttendanceUserSummary s : summaries.values()) {
                w.write(String.format("%s,%s,%d,%d,%d,%.2f\n",
                        csvCell(s.userName()), csvIdCell(s.userId()),
                        s.totalDays(), s.overtimeDays(), s.notSignedDays(), s.equivalentDays()));
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("导出CSV生成失败", e);
        }

        String fileName = "考勤记录_" + from.format(DATE_FMT) + "_" + to.format(DATE_FMT) + ".csv";
        return new AttendanceExportResult(baos.toByteArray(), fileName);
    }

    private String csvCell(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String csvIdCell(String value) {
        if (value == null) return "";
        return "=\"" + value + "\"";
    }

    private LocalTime toLocalTime(AttendanceRecord record) {
        if (record == null || record.getUserCheckTime() == null) return null;
        return record.getUserCheckTime().atZone(ZONE).toLocalTime();
    }

    private long hoursBetween(LocalTime start, LocalTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 0) minutes += 24 * 60;
        return minutes / 60;
    }
}
