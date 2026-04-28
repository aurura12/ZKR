package com.smartlab.erp.finance.repository;

import com.smartlab.erp.finance.entity.FinanceCostBatch;
import com.smartlab.erp.finance.enums.FinanceBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinanceCostBatchRepository extends JpaRepository<FinanceCostBatch, Long> {
    Optional<FinanceCostBatch> findTopByLedgerMonthOrderByIdDesc(String ledgerMonth);

    Optional<FinanceCostBatch> findTopByStatusOrderByCompletedAtDescIdDesc(FinanceBatchStatus status);
}
