package com.smartlab.erp.finance.controller;

import com.smartlab.erp.finance.dto.BatchJobControlVO;
import com.smartlab.erp.finance.dto.BatchProjectCostControlVO;
import com.smartlab.erp.finance.dto.FinanceApiResponse;
import com.smartlab.erp.finance.entity.BatchJobControl;
import com.smartlab.erp.finance.service.BatchControlService;
import com.smartlab.erp.finance.service.FinanceCostBatchService;
import com.smartlab.erp.service.DingTalkAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class BatchWorkbenchController {

    private final BatchControlService batchControlService;
    private final FinanceCostBatchService financeCostBatchService;
    private final DingTalkAttendanceService dingTalkAttendanceService;

    @GetMapping("/jobs")
    public ResponseEntity<FinanceApiResponse<List<BatchJobControlVO>>> listJobs() {
        return ok(FinanceApiResponse.success("batch jobs loaded", batchControlService.listJobControls()));
    }

    @PutMapping("/jobs")
    public ResponseEntity<FinanceApiResponse<BatchJobControlVO>> updateJob(
            @RequestParam String jobKey,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer frequencyMinutes,
            @RequestParam(required = false) Integer runAtHour,
            @RequestParam(required = false) Integer runAtMinute) {
        return ok(FinanceApiResponse.success("job updated",
                batchControlService.updateJobControl(jobKey, enabled, frequencyMinutes, runAtHour, runAtMinute)));
    }

    @PostMapping("/jobs/run")
    public ResponseEntity<FinanceApiResponse<BatchJobControlVO>> triggerJob(@RequestParam String jobKey) {
        BatchJobControl job = batchControlService.findJob(jobKey);
        if (job == null) {
            return ok(FinanceApiResponse.failure("Job not found: " + jobKey, UUID.randomUUID().toString()));
        }

        String triggeredKey = UUID.randomUUID().toString();
        String status;
        String message;

        try {
            if ("FINANCE_COST_BATCH".equals(jobKey)) {
                String ledgerMonth = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                        .minusDays(1).toString().substring(0, 7);
                var result = financeCostBatchService.runBatch(ledgerMonth, false);
                status = "COMPLETED";
                message = "batchId=" + result.getBatchId() + ", records=" + result.getGeneratedRecordCount();
            } else if ("ATTENDANCE_PULL".equals(jobKey)) {
                LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
                List<String> allUsers = dingTalkAttendanceService.fetchAllDingTalkUsers();
                for (int i = 0; i < allUsers.size(); i += 50) {
                    dingTalkAttendanceService.pullAttendance(
                            allUsers.subList(i, Math.min(i + 50, allUsers.size())), yesterday, yesterday);
                }
                status = "COMPLETED";
                message = "pulled for " + yesterday;
            } else {
                status = "FAILED";
                message = "unsupported job key: " + jobKey;
            }

            batchControlService.recordJobRun(jobKey, triggeredKey, status, message);
            return ok(FinanceApiResponse.success("job executed: " + status,
                    toJobVO(batchControlService.findJob(jobKey))));
        } catch (Exception ex) {
            status = "FAILED";
            message = ex.getMessage();
            batchControlService.recordJobRun(jobKey, triggeredKey, status, message);
            return ok(FinanceApiResponse.failure("job failed: " + message, triggeredKey));
        }
    }

    @GetMapping("/projects")
    public ResponseEntity<FinanceApiResponse<List<BatchProjectCostControlVO>>> listProjectControls() {
        return ok(FinanceApiResponse.success("project controls loaded",
                batchControlService.listProjectCostControls()));
    }

    @PutMapping("/projects")
    public ResponseEntity<FinanceApiResponse<BatchProjectCostControlVO>> updateProjectControl(
            @RequestParam String projectId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String note) {
        return ok(FinanceApiResponse.success("project control updated",
                batchControlService.updateProjectCostControl(projectId, enabled, priority, note)));
    }

    private <T> ResponseEntity<FinanceApiResponse<T>> ok(FinanceApiResponse<T> response) {
        return ResponseEntity.ok(response);
    }

    private BatchJobControlVO toJobVO(com.smartlab.erp.finance.entity.BatchJobControl job) {
        if (job == null) return null;
        return BatchJobControlVO.builder()
                .jobKey(job.getJobKey())
                .displayName(job.getDisplayName())
                .enabled(job.getEnabled())
                .scheduleMode(job.getScheduleMode())
                .frequencyMinutes(job.getFrequencyMinutes())
                .runAtHour(job.getRunAtHour())
                .runAtMinute(job.getRunAtMinute())
                .lastTriggeredAt(job.getLastTriggeredAt())
                .lastTriggeredKey(job.getLastTriggeredKey())
                .lastStatus(job.getLastStatus())
                .lastMessage(job.getLastMessage())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
