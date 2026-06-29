package com.smartlab.erp.controller;

import com.smartlab.erp.entity.AttendanceAdjustment;
import com.smartlab.erp.entity.AttendanceRecord;
import com.smartlab.erp.service.AttendanceService;
import com.smartlab.erp.service.DingTalkAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @GetMapping("/summary")
    public ResponseEntity<List<AttendanceService.AttendanceUserSummary>> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(attendanceService.getAttendanceSummary(from, to));
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
    @PreAuthorize("isAuthenticated() and (hasRole('ADMIN') or hasAuthority('SCOPE_system') or principal.accountDomain == T(com.smartlab.erp.enums.AccountDomain).FINANCE)")
    public ResponseEntity<Void> triggerPull(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<String> users = dingTalkService.fetchAllDingTalkUsers();
        LocalDate chunkStart = from;
        while (!chunkStart.isAfter(to)) {
            LocalDate chunkEnd = chunkStart.plusDays(6);
            if (chunkEnd.isAfter(to)) {
                chunkEnd = to;
            }
            for (int i = 0; i < users.size(); i += 50) {
                dingTalkService.pullAttendance(users.subList(i, Math.min(i + 50, users.size())), chunkStart, chunkEnd);
            }
            chunkStart = chunkEnd.plusDays(1);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> exportAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AttendanceService.AttendanceExportResult result = attendanceService.generateAttendanceExport(from, to);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .contentLength(result.csvBytes().length)
                .body(new ByteArrayResource(result.csvBytes()));
    }
}
