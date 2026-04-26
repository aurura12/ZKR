package com.smartlab.erp.finance.repository;

import com.smartlab.erp.finance.entity.BatchJobControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchJobControlRepository extends JpaRepository<BatchJobControl, String> {
}
