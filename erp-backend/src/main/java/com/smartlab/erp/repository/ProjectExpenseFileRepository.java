package com.smartlab.erp.repository;

import com.smartlab.erp.entity.ProjectExpenseFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectExpenseFileRepository extends JpaRepository<ProjectExpenseFile, Long> {
    List<ProjectExpenseFile> findByExpenseIdOrderByCreatedAtAsc(Long expenseId);
}
