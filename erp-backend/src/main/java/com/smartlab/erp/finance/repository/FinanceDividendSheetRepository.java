package com.smartlab.erp.finance.repository;

import com.smartlab.erp.finance.entity.FinanceDividendSheet;
import com.smartlab.erp.finance.enums.FinanceDividendStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinanceDividendSheetRepository extends JpaRepository<FinanceDividendSheet, Long> {
    List<FinanceDividendSheet> findByStatus(FinanceDividendStatus status);

    List<FinanceDividendSheet> findByProject_ProjectIdAndStatus(String projectId, FinanceDividendStatus status);

    List<FinanceDividendSheet> findByProject_ProjectIdAndStatusOrderByConfirmedAtDescIdDesc(String projectId, FinanceDividendStatus status);

    List<FinanceDividendSheet> findByProject_ProjectIdOrderByIdDesc(String projectId);

    boolean existsByProject_ProjectIdAndStatus(String projectId, FinanceDividendStatus status);

    List<FinanceDividendSheet> findByUser_UserIdOrderByCreatedAtDesc(String userId);

    List<FinanceDividendSheet> findByUser_UserIdAndStatusOrderByCreatedAtDesc(String userId, FinanceDividendStatus status);
}
