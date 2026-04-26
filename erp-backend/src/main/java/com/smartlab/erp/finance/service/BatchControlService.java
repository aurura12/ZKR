package com.smartlab.erp.finance.service;

import com.smartlab.erp.config.DynamicBatchSchedulerConfig;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.finance.dto.BatchJobControlVO;
import com.smartlab.erp.finance.dto.BatchProjectCostControlVO;
import com.smartlab.erp.finance.entity.BatchJobControl;
import com.smartlab.erp.finance.entity.BatchProjectCostControl;
import com.smartlab.erp.finance.repository.BatchJobControlRepository;
import com.smartlab.erp.finance.repository.BatchProjectCostControlRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchControlService {

    private final BatchJobControlRepository jobControlRepository;
    private final BatchProjectCostControlRepository projectCostControlRepository;
    private final SysProjectRepository projectRepository;
    private final ApplicationContext ctx;

    @Transactional(readOnly = true)
    public List<BatchJobControlVO> listJobControls() {
        return jobControlRepository.findAll().stream()
                .map(this::toJobVO)
                .toList();
    }

    @Transactional(readOnly = true)
    public BatchJobControl findJob(String jobKey) {
        return jobControlRepository.findById(jobKey).orElse(null);
    }

    @Transactional
    public BatchJobControlVO updateJobControl(String jobKey, Boolean enabled, Integer frequencyMinutes, Integer runAtHour, Integer runAtMinute) {
        BatchJobControl job = jobControlRepository.findById(jobKey)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobKey));
        if (enabled != null) job.setEnabled(enabled);
        if (frequencyMinutes != null) job.setFrequencyMinutes(frequencyMinutes);
        if (runAtHour != null) job.setRunAtHour(runAtHour);
        if (runAtMinute != null) job.setRunAtMinute(runAtMinute);
        job = jobControlRepository.save(job);
        jobControlRepository.flush();
        ctx.getBean(DynamicBatchSchedulerConfig.class).triggerReschedule(jobKey);
        return toJobVO(job);
    }

    @Transactional
    public BatchJobControlVO recordJobRun(String jobKey, String triggeredKey, String status, String message) {
        BatchJobControl job = jobControlRepository.findById(jobKey)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobKey));
        job.setLastTriggeredAt(java.time.Instant.now());
        job.setLastTriggeredKey(triggeredKey);
        job.setLastStatus(status);
        job.setLastMessage(message);
        job = jobControlRepository.save(job);
        return toJobVO(job);
    }

    @Transactional(readOnly = true)
    public List<BatchProjectCostControlVO> listProjectCostControls() {
        return projectCostControlRepository.findAll().stream()
                .map(this::toProjectVO)
                .toList();
    }

    @Transactional
    public BatchProjectCostControlVO updateProjectCostControl(String projectId, Boolean enabled, Integer priority, String note) {
        BatchProjectCostControl ctrl = projectCostControlRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project control not found: " + projectId));
        if (enabled != null) ctrl.setEnabled(enabled);
        if (priority != null) ctrl.setPriority(priority);
        if (note != null) ctrl.setNote(note);
        ctrl = projectCostControlRepository.save(ctrl);
        return toProjectVO(ctrl);
    }

    @Transactional(readOnly = true)
    public List<String> getEnabledProjectIds() {
        return projectCostControlRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .map(BatchProjectCostControl::getProjectId)
                .toList();
    }

    private BatchJobControlVO toJobVO(BatchJobControl job) {
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

    private BatchProjectCostControlVO toProjectVO(BatchProjectCostControl ctrl) {
        String projectName = projectRepository.findById(ctrl.getProjectId())
                .map(SysProject::getName)
                .orElse(null);
        return BatchProjectCostControlVO.builder()
                .projectId(ctrl.getProjectId())
                .projectName(projectName)
                .enabled(ctrl.getEnabled())
                .priority(ctrl.getPriority())
                .note(ctrl.getNote())
                .updatedAt(ctrl.getUpdatedAt())
                .build();
    }
}
