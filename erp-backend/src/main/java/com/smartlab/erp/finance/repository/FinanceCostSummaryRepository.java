package com.smartlab.erp.finance.repository;

import com.smartlab.erp.finance.entity.FinanceCostSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinanceCostSummaryRepository extends JpaRepository<FinanceCostSummary, Long> {
    Optional<FinanceCostSummary> findByProject_ProjectIdAndLedgerMonth(String projectId, String ledgerMonth);

    List<FinanceCostSummary> findByLedgerMonth(String ledgerMonth);

    Optional<FinanceCostSummary> findTopByProject_ProjectIdOrderByIdDesc(String projectId);

    Optional<FinanceCostSummary> findTopByProject_ProjectIdOrderByLedgerMonthDescIdDesc(String projectId);

    List<FinanceCostSummary> findByProject_ProjectIdIn(List<String> projectIds);
}
