package com.smartlab.erp.finance.repository;

import com.smartlab.erp.finance.entity.FinanceCostBatch;
import com.smartlab.erp.finance.enums.FinanceBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FinanceCostBatchRepository extends JpaRepository<FinanceCostBatch, Long> {
    Optional<FinanceCostBatch> findTopByLedgerMonthOrderByIdDesc(String ledgerMonth);

    Optional<FinanceCostBatch> findTopByStatusOrderByCompletedAtDescIdDesc(FinanceBatchStatus status);

    @Modifying
    @Query(value = "DELETE FROM finance_cost_batch WHERE ledger_month = :ledgerMonth", nativeQuery = true)
    void deleteByLedgerMonth(@Param("ledgerMonth") String ledgerMonth);
}
