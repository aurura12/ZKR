package com.smartlab.erp.finance.schedule;

import com.smartlab.erp.finance.entity.BatchJobControl;
import com.smartlab.erp.finance.repository.BatchJobControlRepository;
import com.smartlab.erp.finance.service.FinanceCostBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceCostBatchScheduler {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LEDGER_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final FinanceCostBatchService financeCostBatchService;
    private final BatchJobControlRepository jobControlRepository;
    private static final String JOB_KEY = "FINANCE_COST_BATCH";

    public void runNightlyBatch() {
        Optional<BatchJobControl> optJob = jobControlRepository.findById(JOB_KEY);
        if (optJob.isEmpty() || !Boolean.TRUE.equals(optJob.get().getEnabled())) {
            log.info("[Scheduler] Cost batch is disabled, skipping.");
            return;
        }
        String ledgerMonth = ZonedDateTime.now(SHANGHAI_ZONE).minusDays(1).format(LEDGER_MONTH_FORMATTER);
        try {
            var result = financeCostBatchService.runBatch(ledgerMonth, true);
            recordRun("COMPLETED", "batchId=" + result.getBatchId() + ", records=" + result.getGeneratedRecordCount());
            log.info("Nightly project labor cost batch completed for {} with batchId={} and generatedRecordCount={}",
                    ledgerMonth, result.getBatchId(), result.getGeneratedRecordCount());
        } catch (RuntimeException ex) {
            recordRun("FAILED", ex.getMessage());
            log.error("Nightly project labor cost batch failed for {}", ledgerMonth, ex);
        }
    }

    private void recordRun(String status, String message) {
        try {
            jobControlRepository.findById(JOB_KEY).ifPresent(job -> {
                job.setLastTriggeredAt(java.time.Instant.now());
                job.setLastStatus(status);
                job.setLastMessage(message);
                jobControlRepository.save(job);
            });
        } catch (Exception ignored) {}
    }
}
