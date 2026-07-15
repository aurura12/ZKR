package com.smartlab.erp.repository;

import com.smartlab.erp.entity.WorkOrder;
import com.smartlab.erp.entity.WorkOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    List<WorkOrder> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<WorkOrder> findByProjectIdAndAssigneeIdOrderByCreatedAtDesc(String projectId, String assigneeId);

    List<WorkOrder> findByProjectIdAndStatusOrderByCreatedAtDesc(String projectId, WorkOrderStatus status);

    long countByProjectIdAndStatus(String projectId, WorkOrderStatus status);

    long countByAssigneeIdAndStatus(String assigneeId, WorkOrderStatus status);
}
