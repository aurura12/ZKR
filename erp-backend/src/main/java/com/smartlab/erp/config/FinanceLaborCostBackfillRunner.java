package com.smartlab.erp.config;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.finance.enums.FinanceBatchStatus;
import com.smartlab.erp.finance.repository.FinanceCostBatchRepository;
import com.smartlab.erp.finance.repository.FinanceCostEntryRepository;
import com.smartlab.erp.finance.repository.FinanceCostSummaryRepository;
import com.smartlab.erp.finance.service.FinanceCostBatchService;
import com.smartlab.erp.repository.SysProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FinanceLaborCostBackfillRunner {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private final FinanceCostEntryRepository financeCostEntryRepository;
    private final FinanceCostSummaryRepository financeCostSummaryRepository;
    private final FinanceCostBatchRepository financeCostBatchRepository;
    private final FinanceCostBatchService financeCostBatchService;
    private final SysProjectRepository projectRepository;
    private final TransactionTemplate transactionTemplate;

    @Bean
    @DependsOn("migrateUserSchema")
    @Order(100)
    ApplicationRunner backfillDailyLaborCosts() {
        return args -> {
            log.info("Starting cost backfill runner (skip-if-done mode)...");

            var projects = projectRepository.findAll().stream()
                    .filter(project -> project != null && project.getFlowType() == FlowType.PROJECT)
                    .toList();
            if (projects.isEmpty()) {
                log.info("No projects found. Skipping backfill.");
                return;
            }

            LocalDate earliestStart = projects.stream()
                    .map(SysProject::getCreatedAt)
                    .filter(java.util.Objects::nonNull)
                    .map(instant -> instant.atZone(SHANGHAI_ZONE).toLocalDate())
                    .min(Comparator.naturalOrder())
                    .orElse(LocalDate.now(SHANGHAI_ZONE));

            YearMonth cursor = YearMonth.from(earliestStart);
            YearMonth current = YearMonth.now(SHANGHAI_ZONE);
            log.info("Backfill range: {} to {}", cursor, current);
            while (!cursor.isAfter(current)) {
                String month = cursor.toString();
                try {
                    boolean alreadyDone = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                            financeCostBatchRepository.findTopByLedgerMonthOrderByIdDesc(month)
                                    .map(batch -> batch.getStatus() == FinanceBatchStatus.COMPLETED)
                                    .orElse(false)));
                    if (alreadyDone) {
                        log.info("Month {} already has a COMPLETED batch. Skipping.", month);
                    } else {
                        transactionTemplate.executeWithoutResult(status ->
                                financeCostBatchService.runBatch(month, true));
                        log.info("Backfill completed for month: {}", month);
                    }
                } catch (Exception e) {
                    log.error("Backfill failed for month: {}. Continuing with next month.", month, e);
                }
                cursor = cursor.plusMonths(1);
            }
            log.info("Cost backfill runner finished.");
        };
    }

    void truncateAllCostData() {
        financeCostEntryRepository.deleteAll();
        financeCostSummaryRepository.deleteAll();
        financeCostBatchRepository.deleteAll();
    }
}
