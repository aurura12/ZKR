package com.smartlab.erp.repository;

import com.smartlab.erp.entity.CompanyExpense;
import com.smartlab.erp.enums.CompanyExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface CompanyExpenseRepository extends JpaRepository<CompanyExpense, Long> {
    List<CompanyExpense> findByApprovalStatusAndChenleiAtBetween(
            CompanyExpenseStatus status, Instant start, Instant end);
    long countByApprovalStatus(CompanyExpenseStatus status);
}
