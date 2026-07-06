package com.smartlab.erp.repository;

import com.smartlab.erp.entity.ContractLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractLedgerRepository extends JpaRepository<ContractLedger, Long> {

    List<ContractLedger> findByExpenseIdOrderById(Long expenseId);

    Optional<ContractLedger> findByContractNo(String contractNo);
}
