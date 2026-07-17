package com.smartlab.erp.repository;

import com.smartlab.erp.entity.ProjectExpense;
import com.smartlab.erp.enums.ProjectExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectExpenseRepository extends JpaRepository<ProjectExpense, Long> {
    List<ProjectExpense> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<ProjectExpense> findByStatusOrderByCreatedAtDesc(ProjectExpenseStatus status);

    @Query("SELECT e FROM ProjectExpense e WHERE e.status IN (:statuses) ORDER BY e.createdAt DESC")
    List<ProjectExpense> findByStatusInOrderByCreatedAtDesc(@Param("statuses") List<ProjectExpenseStatus> statuses);

    @Query("SELECT e FROM ProjectExpense e WHERE e.jiaomiaoAction IS NOT NULL OR e.financeAction IS NOT NULL OR e.chenleiAction IS NOT NULL ORDER BY e.updatedAt DESC")
    List<ProjectExpense> findAllReviewed();

    long countByStatus(ProjectExpenseStatus status);

    List<ProjectExpense> findBySubmitterUserIdOrderByCreatedAtDesc(String submitterUserId);
}
