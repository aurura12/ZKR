package com.smartlab.erp.service;

import com.smartlab.erp.entity.WorkflowMemberRole;
import com.smartlab.erp.repository.WorkflowMemberRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowMemberRoleSyncServiceTest {

    @Mock
    private WorkflowMemberRoleRepository workflowMemberRoleRepository;

    @InjectMocks
    private WorkflowMemberRoleSyncService workflowMemberRoleSyncService;

    @Test
    void skipsDuplicateWorkflowMemberRoleWrites() {
        when(workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowIdAndUserIdAndRole("PROJECT", "p-1", "u-1", "MEMBER"))
                .thenReturn(Optional.of(WorkflowMemberRole.builder().build()));

        workflowMemberRoleSyncService.sync("PROJECT", "p-1", "u-1", "MEMBER");

        verify(workflowMemberRoleRepository, never()).save(any());
    }
}
