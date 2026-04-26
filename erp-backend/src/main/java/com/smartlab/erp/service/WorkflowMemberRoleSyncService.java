package com.smartlab.erp.service;

import com.smartlab.erp.entity.WorkflowMemberRole;
import com.smartlab.erp.repository.WorkflowMemberRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowMemberRoleSyncService {

    private final WorkflowMemberRoleRepository workflowMemberRoleRepository;

    public void sync(String workflowType, String workflowId, String userId, String role) {
        if (isBlank(workflowType) || isBlank(workflowId) || isBlank(userId) || isBlank(role)) {
            return;
        }
        if (workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowIdAndUserIdAndRole(workflowType, workflowId, userId, role).isPresent()) {
            return;
        }
        workflowMemberRoleRepository.save(WorkflowMemberRole.builder()
                .workflowType(workflowType)
                .workflowId(workflowId)
                .userId(userId)
                .role(role)
                .build());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
