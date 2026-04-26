package com.smartlab.erp.repository;

import com.smartlab.erp.entity.WorkflowMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowMemberRoleRepository extends JpaRepository<WorkflowMemberRole, Long> {

    List<WorkflowMemberRole> findByWorkflowTypeAndWorkflowId(String workflowType, String workflowId);

    List<WorkflowMemberRole> findByWorkflowType(String workflowType);

    List<WorkflowMemberRole> findByUserId(String userId);

    Optional<WorkflowMemberRole> findByWorkflowTypeAndWorkflowIdAndUserIdAndRole(String workflowType,
                                                                                  String workflowId,
                                                                                  String userId,
                                                                                  String role);
}
