package com.smartlab.erp.config;

import com.smartlab.erp.finance.entity.BatchJobControl;
import com.smartlab.erp.finance.repository.BatchJobControlRepository;
import com.smartlab.erp.finance.service.FinanceCostBatchService;
import com.smartlab.erp.service.DingTalkAttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class DynamicBatchSchedulerConfig implements SchedulingConfigurer, ApplicationRunner {

    private final BatchJobControlRepository jobControlRepository;
    private final FinanceCostBatchService financeCostBatchService;
    private final DingTalkAttendanceService dingTalkAttendanceService;
    private final TransactionTemplate transactionTemplate;

    private TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("dynamic-scheduler-");
        scheduler.initialize();
        this.taskScheduler = scheduler;
        registrar.setTaskScheduler(scheduler);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (this.taskScheduler == null) {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(4);
            scheduler.setThreadNamePrefix("dynamic-scheduler-");
            scheduler.initialize();
            this.taskScheduler = scheduler;
        }
        List<BatchJobControl> jobs = jobControlRepository.findAll();
        for (BatchJobControl job : jobs) {
            scheduleOrRescheduleJob(job);
        }
        log.info("[DynamicScheduler] Initialized {} batch jobs", jobs.size());
    }

    public void scheduleOrRescheduleJob(BatchJobControl job) {
        String jobKey = job.getJobKey();
        ScheduledFuture<?> existing = scheduledTasks.get(jobKey);
        if (existing != null) {
            existing.cancel(false);
            scheduledTasks.remove(jobKey);
        }

        if (!Boolean.TRUE.equals(job.getEnabled())) {
            log.info("[DynamicScheduler] Job {} is disabled, not scheduling.", jobKey);
            return;
        }

        Runnable task = buildTask(jobKey);
        if (task == null) {
            log.warn("[DynamicScheduler] Unknown job key: {}", jobKey);
            return;
        }

        String cron = buildCron(job);
        CronTrigger trigger = new CronTrigger(cron, ZoneId.of("Asia/Shanghai"));
        ScheduledFuture<?> future = taskScheduler.schedule(task, trigger);
        scheduledTasks.put(jobKey, future);
        log.info("[DynamicScheduler] Scheduled job {} with cron={}", jobKey, cron);
    }

    private String buildCron(BatchJobControl job) {
        String mode = job.getScheduleMode();
        if ("MANUAL_ONLY".equals(mode)) {
            return "0 0 0 * * *";
        }
        int hour = job.getRunAtHour() != null ? job.getRunAtHour() : 0;
        int minute = job.getRunAtMinute() != null ? job.getRunAtMinute() : 0;
        return String.format("0 %d %d * * *", minute, hour);
    }

    private Runnable buildTask(String jobKey) {
        if ("FINANCE_COST_BATCH".equals(jobKey)) {
            return () -> runCostBatchJob(jobKey);
        }
        if ("ATTENDANCE_PULL".equals(jobKey)) {
            return () -> runAttendanceDailyJob(jobKey);
        }
        return null;
    }

    private void runCostBatchJob(String jobKey) {
        try {
            String ledgerMonth = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                    .minusDays(1).toString().substring(0, 7);
            var result = transactionTemplate.execute(status -> financeCostBatchService.runBatch(ledgerMonth, true));
            if (result != null) {
                recordRun(jobKey, "COMPLETED",
                        "batchId=" + result.getBatchId() + ", records=" + result.getGeneratedRecordCount());
                log.info("[Scheduler] Cost batch completed for {}: batchId={}, records={}",
                        ledgerMonth, result.getBatchId(), result.getGeneratedRecordCount());
            }
        } catch (Exception ex) {
            recordRun(jobKey, "FAILED", ex.getMessage());
            log.error("[Scheduler] Cost batch failed", ex);
        }
    }

    private void runAttendanceDailyJob(String jobKey) {
        try {
            LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
            transactionTemplate.execute(status -> {
                List<String> allUsers = dingTalkAttendanceService.fetchAllDingTalkUsers();
                for (int i = 0; i < allUsers.size(); i += 50) {
                    dingTalkAttendanceService.pullAttendance(
                            allUsers.subList(i, Math.min(i + 50, allUsers.size())), yesterday, yesterday);
                }
                return null;
            });
            recordRun(jobKey, "COMPLETED", "pulled for " + yesterday);
            log.info("[Scheduler] Attendance daily pull completed for {}", yesterday);
        } catch (Exception ex) {
            recordRun(jobKey, "FAILED", ex.getMessage());
            log.error("[Scheduler] Attendance daily pull failed", ex);
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
        } catch (Exception ex) {
            log.warn("[Scheduler] Failed to record run status for {}: {}", jobKey, ex.getMessage());
        }
    }

    public void triggerReschedule(String jobKey) {
        jobControlRepository.findById(jobKey).ifPresent(this::scheduleOrRescheduleJob);
    }
}
