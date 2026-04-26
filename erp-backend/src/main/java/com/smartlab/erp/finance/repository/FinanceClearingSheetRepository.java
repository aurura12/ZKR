package com.smartlab.erp.finance.repository;

import com.smartlab.erp.finance.entity.FinanceClearingSheet;
import com.smartlab.erp.finance.enums.FinanceClearingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinanceClearingSheetRepository extends JpaRepository<FinanceClearingSheet, Long> {
    List<FinanceClearingSheet> findByStatus(FinanceClearingStatus status);

    Optional<FinanceClearingSheet> findTopByProject_ProjectIdOrderByIdDesc(String projectId);

    Optional<FinanceClearingSheet> findTopByProject_ProjectIdOrderByLedgerMonthDescIdDesc(String projectId);

    Optional<FinanceClearingSheet> findByProject_ProjectIdAndLedgerMonth(String projectId, String ledgerMonth);
}
