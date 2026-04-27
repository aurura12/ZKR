package com.smartlab.erp.repository;

import com.smartlab.erp.entity.ProjectCostAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectCostAdjustmentRepository extends JpaRepository<ProjectCostAdjustment, Long> {
    List<ProjectCostAdjustment> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
