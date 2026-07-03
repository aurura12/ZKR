package com.smartlab.erp.repository;

import com.smartlab.erp.entity.InvoiceLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceLedgerRepository extends JpaRepository<InvoiceLedger, Long> {

    List<InvoiceLedger> findByExpenseIdOrderBySeqNo(Long expenseId);
}
