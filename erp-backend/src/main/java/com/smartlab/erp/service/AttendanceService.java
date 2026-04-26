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
import java.time.LocalDate;
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
}
