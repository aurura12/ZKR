package com.smartlab.erp.scheduler;

import com.smartlab.erp.finance.entity.BatchJobControl;
import com.smartlab.erp.finance.repository.BatchJobControlRepository;
import com.smartlab.erp.service.DingTalkAttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DingTalkAttendanceScheduler {

    private static final String JOB_KEY_DAILY = "ATTENDANCE_PULL";
    private static final String JOB_KEY_HISTORY = "ATTENDANCE_HISTORY_PULL";
    private final DingTalkAttendanceService attendanceService;
    private final BatchJobControlRepository jobControlRepository;
    private static final LocalDate HISTORY_START = LocalDate.of(2026, 4, 1);

    public void pullYesterdayAttendance() {
        Optional<BatchJobControl> optJob = jobControlRepository.findById(JOB_KEY_DAILY);
        if (optJob.isEmpty() || !Boolean.TRUE.equals(optJob.get().getEnabled())) {
            log.info("[Scheduler] Attendance daily pull is disabled, skipping.");
            return;
        }
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("[Scheduler] Starting daily attendance pull for {}", yesterday);
        try {
            List<String> allUsers = attendanceService.fetchAllDingTalkUsers();
            for (int i = 0; i < allUsers.size(); i += 50) {
                List<String> batch = allUsers.subList(i, Math.min(i + 50, allUsers.size()));
                attendanceService.pullAttendance(batch, yesterday, yesterday);
            }
            recordRun(JOB_KEY_DAILY, "COMPLETED", "pulled for " + yesterday);
            log.info("[Scheduler] Daily attendance pull completed for {}", yesterday);
        } catch (Exception e) {
            recordRun(JOB_KEY_DAILY, "FAILED", e.getMessage());
            log.error("[Scheduler] Daily attendance pull failed", e);
        }
    }

    public void pullHistoricalAttendance() {
        Optional<BatchJobControl> optJob = jobControlRepository.findById(JOB_KEY_HISTORY);
        if (optJob.isEmpty() || !Boolean.TRUE.equals(optJob.get().getEnabled())) {
            log.info("[Scheduler] Attendance history pull is disabled, skipping.");
            return;
        }
        log.info("[Scheduler] Starting historical attendance pull from {} to yesterday", HISTORY_START);
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate cursor = HISTORY_START;
        int batchSize = 7;
        int totalBatches = (int) ((end.toEpochDay() - HISTORY_START.toEpochDay()) / batchSize + 1);
        int pulled = 0;
        try {
            List<String> allUsers = attendanceService.fetchAllDingTalkUsers();
            while (!cursor.isAfter(end)) {
                LocalDate batchEnd = cursor.plusDays(batchSize - 1);
                if (batchEnd.isAfter(end)) batchEnd = end;
                for (int i = 0; i < allUsers.size(); i += 50) {
                    List<String> batch = allUsers.subList(i, Math.min(i + 50, allUsers.size()));
                    attendanceService.pullAttendance(batch, cursor, batchEnd);
                }
                pulled++;
                log.info("[Scheduler] Historical batch {}/{}: {} ~ {}", pulled, totalBatches, cursor, batchEnd);
                cursor = batchEnd.plusDays(1);
            }
            recordRun(JOB_KEY_HISTORY, "COMPLETED", "total batches=" + pulled);
            log.info("[Scheduler] Historical attendance pull completed. Total batches: {}", pulled);
        } catch (Exception e) {
            recordRun(JOB_KEY_HISTORY, "FAILED", e.getMessage());
            log.error("[Scheduler] Historical attendance pull failed", e);
        }
    }

    private void recordRun(String jobKey, String status, String message) {
        try {
            jobControlRepository.findById(jobKey).ifPresent(job -> {
                job.setLastTriggeredAt(Instant.now());
                job.setLastStatus(status);
                job.setLastMessage(message);
                jobControlRepository.save(job);
            });
        } catch (Exception ignored) {}
    }
}
