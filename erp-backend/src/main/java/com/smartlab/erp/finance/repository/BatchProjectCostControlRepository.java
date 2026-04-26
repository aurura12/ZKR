package com.smartlab.erp.finance.repository;

import com.smartlab.erp.finance.entity.BatchProjectCostControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchProjectCostControlRepository extends JpaRepository<BatchProjectCostControl, String> {
}
