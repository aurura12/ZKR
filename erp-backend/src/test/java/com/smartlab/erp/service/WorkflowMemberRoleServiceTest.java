package com.smartlab.erp.service;

import com.smartlab.erp.dto.WorkflowMemberRoleDTO;
import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProductIdeaDetail;
import com.smartlab.erp.entity.ResearchProjectProfile;
import com.smartlab.erp.entity.ResearchStatus;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.entity.WorkflowMemberRole;
import com.smartlab.erp.repository.ProductIdeaDetailRepository;
import com.smartlab.erp.repository.ResearchProjectProfileRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.repository.WorkflowMemberRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowMemberRoleServiceTest {

    @Mock
    private WorkflowMemberRoleRepository workflowMemberRoleRepository;
    @Mock
    private SysProjectRepository projectRepository;
    @Mock
    private SysProjectMemberRepository projectMemberRepository;
    @Mock
    private ProductIdeaDetailRepository productIdeaDetailRepository;
    @Mock
    private ResearchProjectProfileRepository researchProjectProfileRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WorkflowMemberRoleService workflowMemberRoleService;

    @Test
    void expandsNewTableRowsByUserAndRole() {
        when(workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowId("PROJECT", "p-1")).thenReturn(List.of(
                WorkflowMemberRole.builder().workflowType("PROJECT").workflowId("p-1").userId("u-1").role("DEV").build(),
                WorkflowMemberRole.builder().workflowType("PROJECT").workflowId("p-1").userId("u-1").role("ALGORITHM").build()
        ));
        when(userRepository.findById("u-1")).thenReturn(Optional.of(user("u-1", "alice", "Alice")));

        List<WorkflowMemberRoleDTO> rows = workflowMemberRoleService.getWorkflowMemberRoles("PROJECT", "p-1");

        assertThat(rows).extracting(WorkflowMemberRoleDTO::getRole).containsExactly("DEV", "ALGORITHM");
        assertThat(rows).allSatisfy(row -> assertThat(row.getUserId()).isEqualTo("u-1"));
    }

    @Test
    void prefersNewTableOverLegacyFallback() {
        when(workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowId("PROJECT", "p-2")).thenReturn(List.of(
                WorkflowMemberRole.builder().workflowType("PROJECT").workflowId("p-2").userId("u-2").role("MEMBER").build()
        ));

        List<WorkflowMemberRoleDTO> rows = workflowMemberRoleService.getWorkflowMemberRoles("PROJECT", "p-2");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRole()).isEqualTo("MEMBER");
    }

    @Test
    void fallsBackToLegacyProjectMembersWhenNewTableEmpty() {
        when(workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowId("PROJECT", "p-3")).thenReturn(List.of());
        when(projectMemberRepository.findByProjectIdWithUser("p-3")).thenReturn(List.of(
                SysProjectMember.builder().projectId("p-3").user(user("u-3", "bob", "Bob")).role("DATA_ENGINEER").build(),
                SysProjectMember.builder().projectId("p-3").user(user("u-3", "bob", "Bob")).role("DEV").build()
        ));

        List<WorkflowMemberRoleDTO> rows = workflowMemberRoleService.getWorkflowMemberRoles("PROJECT", "p-3");

        assertThat(rows).extracting(WorkflowMemberRoleDTO::getRole).containsExactly("DATA_ENGINEER", "DEV");
    }

    @Test
    void fallsBackToLegacyResearchOwnersAndMembersWhenNewTableEmpty() {
        User owner = user("u-4", "carol", "Carol");
        User host = user("u-5", "dave", "Dave");
        User chief = user("u-6", "erin", "Erin");
        when(workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowId("RESEARCH", "r-1")).thenReturn(List.of());
        when(projectRepository.findById("r-1")).thenReturn(Optional.of(SysProject.builder().projectId("r-1").flowType(FlowType.RESEARCH).manager(owner).build()));
        when(researchProjectProfileRepository.findByProjectId("r-1")).thenReturn(Optional.of(ResearchProjectProfile.builder()
                .projectId("r-1")
                .status(ResearchStatus.INIT)
                .ideaOwnerUserId(owner.getUserId())
                .hostUserId(host.getUserId())
                .chiefEngineerUserId(chief.getUserId())
                .blueprintOwnerUserId(host.getUserId())
                .architectureOwnerUserId(chief.getUserId())
                .taskBreakdownOwnerUserId(chief.getUserId())
                .evaluationReportOwnerUserId(chief.getUserId())
                .build()));
        when(projectMemberRepository.findByProjectIdWithUser("r-1")).thenReturn(List.of(
                SysProjectMember.builder().projectId("r-1").user(owner).role("RESEARCH").build(),
                SysProjectMember.builder().projectId("r-1").user(host).role("HOST").build(),
                SysProjectMember.builder().projectId("r-1").user(chief).role("CHIEF_ENGINEER").build()
        ));
        when(userRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(switch (invocation.getArgument(0, String.class)) {
            case "u-4" -> owner;
            case "u-5" -> host;
            case "u-6" -> chief;
            default -> null;
        }));

        List<WorkflowMemberRoleDTO> rows = workflowMemberRoleService.getWorkflowMemberRoles("RESEARCH", "r-1");

        assertThat(rows).extracting(WorkflowMemberRoleDTO::getRole)
                .contains("RESEARCH", "HOST", "CHIEF_ENGINEER", "BLUEPRINT_OWNER", "ARCHITECTURE_OWNER", "TASK_BREAKDOWN_OWNER", "EVALUATION_REPORT_OWNER");
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getUserId()).isEqualTo("u-5");
            assertThat(row.getRole()).isEqualTo("HOST");
        });
    }

    private User user(String userId, String username, String name) {
        return User.builder()
                .userId(userId)
                .username(username)
                .name(name)
                .role("MEMBER")
                .active(true)
                .build();
    }
}
