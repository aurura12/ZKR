package com.smartlab.erp.finance.repository;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.finance.entity.FinanceCostEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FinanceCostEntryRepository extends JpaRepository<FinanceCostEntry, Long> {
    List<FinanceCostEntry> findByBatch_Id(Long batchId);

    List<FinanceCostEntry> findByProject_ProjectIdAndLedgerMonth(String projectId, String ledgerMonth);

    List<FinanceCostEntry> findByProject_ProjectIdAndLedgerMonthAndAccrualDate(String projectId, String ledgerMonth, LocalDate accrualDate);

    boolean existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(String projectId, String userId, LocalDate accrualDate);

    long countByAccrualDateIsNotNull();

    long countByProject_FlowTypeAndAccrualDateIsNotNull(FlowType flowType);

    void deleteByProject_ProjectIdAndLedgerMonth(String projectId, String ledgerMonth);
}
