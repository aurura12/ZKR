package com.smartlab.erp.config;

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
import com.smartlab.erp.repository.WorkflowMemberRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowMemberRoleMigrationTest {

    @Mock
    private SysProjectRepository projectRepository;
    @Mock
    private SysProjectMemberRepository projectMemberRepository;
    @Mock
    private ResearchProjectProfileRepository researchProjectProfileRepository;
    @Mock
    private ProductIdeaDetailRepository productIdeaDetailRepository;
    @Mock
    private WorkflowMemberRoleRepository workflowMemberRoleRepository;

    @Captor
    private ArgumentCaptor<List<WorkflowMemberRole>> rowsCaptor;

    @Test
    void backfillsOneProjectFlowRoleAndIsIdempotent() throws Exception {
        User user = buildUser("000101");
        SysProject project = SysProject.builder()
                .projectId("project-1")
                .flowType(FlowType.PROJECT)
                .manager(user)
                .build();
        SysProjectMember member = SysProjectMember.builder()
                .projectId("project-1")
                .user(user)
                .role("DATA_ENGINEER")
                .weight(0)
                .build();

        when(workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowIdAndUserIdAndRole("PROJECT", "project-1", "000101", "DATA_ENGINEER"))
                .thenReturn(Optional.empty());
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectMemberRepository.findByProjectId("project-1")).thenReturn(List.of(member));
        when(researchProjectProfileRepository.findAll()).thenReturn(List.of());
        when(productIdeaDetailRepository.findAll()).thenReturn(List.of());

        WorkflowMemberRoleMigration migration = new WorkflowMemberRoleMigration(
                projectRepository,
                projectMemberRepository,
                researchProjectProfileRepository,
                productIdeaDetailRepository,
                workflowMemberRoleRepository
        );

        migration.backfillWorkflowMemberRoles().run(new DefaultApplicationArguments(new String[0]));

        verify(workflowMemberRoleRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).singleElement().satisfies(row -> {
            assertThat(row.getWorkflowType()).isEqualTo("PROJECT");
            assertThat(row.getWorkflowId()).isEqualTo("project-1");
            assertThat(row.getUserId()).isEqualTo("000101");
            assertThat(row.getRole()).isEqualTo("DATA_ENGINEER");
        });
    }

    @Test
    void backfillsResearchAndProductOwnersWithOriginalBusinessRoles() throws Exception {
        User researchUser = buildUser("000201");
        User productUser = buildUser("000202");

        SysProject researchProject = SysProject.builder().projectId("research-1").flowType(FlowType.RESEARCH).manager(researchUser).build();
        SysProject productProject = SysProject.builder().projectId("product-1").flowType(FlowType.PRODUCT).manager(productUser).build();

        ResearchProjectProfile researchProfile = ResearchProjectProfile.builder()
                .projectId("research-1")
                .status(ResearchStatus.INIT)
                .ideaOwnerUserId("000201")
                .hostUserId("000203")
                .chiefEngineerUserId("000204")
                .blueprintOwnerUserId("000203")
                .architectureOwnerUserId("000204")
                .taskBreakdownOwnerUserId("000204")
                .evaluationReportOwnerUserId("000204")
                .build();
        ProductIdeaDetail productDetail = ProductIdeaDetail.builder()
                .projectId("product-1")
                .ideaOwnerUserId("000202")
                .promotionIcUserId("000205")
                .demoEngineeringOwnerUserId("000206")
                .demoFileOwnerUserId("000207")
                .demoDescriptionOwnerUserId("000208")
                .demoFeasibilityOwnerUserId("000209")
                .build();

        when(projectRepository.findAll()).thenReturn(List.of(researchProject, productProject));
        when(projectRepository.findById("research-1")).thenReturn(Optional.of(researchProject));
        when(projectRepository.findById("product-1")).thenReturn(Optional.of(productProject));
        when(projectMemberRepository.findByProjectId(any())).thenReturn(List.of());
        when(researchProjectProfileRepository.findAll()).thenReturn(List.of(researchProfile));
        when(productIdeaDetailRepository.findAll()).thenReturn(List.of(productDetail));
        when(workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowIdAndUserIdAndRole(any(), any(), any(), any())).thenReturn(Optional.empty());

        WorkflowMemberRoleMigration migration = new WorkflowMemberRoleMigration(
                projectRepository,
                projectMemberRepository,
                researchProjectProfileRepository,
                productIdeaDetailRepository,
                workflowMemberRoleRepository
        );

        migration.backfillWorkflowMemberRoles().run(new DefaultApplicationArguments(new String[0]));

        verify(workflowMemberRoleRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).extracting(WorkflowMemberRole::getRole)
                .contains(
                        "RESEARCH",
                        "HOST",
                        "CHIEF_ENGINEER",
                        "BLUEPRINT_OWNER",
                        "ARCHITECTURE_OWNER",
                        "TASK_BREAKDOWN_OWNER",
                        "EVALUATION_REPORT_OWNER",
                        "ADMIN",
                        "PROMOTION_IC",
                        "DEMO_ENG"
                );

        assertThat(rowsCaptor.getValue()).anySatisfy(row -> {
            assertThat(row.getWorkflowType()).isEqualTo("RESEARCH");
            assertThat(row.getWorkflowId()).isEqualTo("research-1");
            assertThat(row.getUserId()).isEqualTo("000203");
            assertThat(row.getRole()).isEqualTo("BLUEPRINT_OWNER");
        });
    }

    @Test
    void backfillsDuplicateWorkflowRoleOnlyOnceAcrossSources() throws Exception {
        User user = buildUser("000301");
        SysProject researchProject = SysProject.builder().projectId("research-dup").flowType(FlowType.RESEARCH).manager(user).build();
        SysProjectMember hostMember = SysProjectMember.builder()
                .projectId("research-dup")
                .user(user)
                .role("HOST")
                .weight(0)
                .build();
        ResearchProjectProfile researchProfile = ResearchProjectProfile.builder()
                .projectId("research-dup")
                .hostUserId("000301")
                .build();

        when(projectRepository.findAll()).thenReturn(List.of(researchProject));
        when(projectRepository.findById("research-dup")).thenReturn(Optional.of(researchProject));
        when(projectMemberRepository.findByProjectId("research-dup")).thenReturn(List.of(hostMember));
        when(researchProjectProfileRepository.findAll()).thenReturn(List.of(researchProfile));
        when(productIdeaDetailRepository.findAll()).thenReturn(List.of());
        when(workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowIdAndUserIdAndRole(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        WorkflowMemberRoleMigration migration = new WorkflowMemberRoleMigration(
                projectRepository,
                projectMemberRepository,
                researchProjectProfileRepository,
                productIdeaDetailRepository,
                workflowMemberRoleRepository
        );

        migration.backfillWorkflowMemberRoles().run(new DefaultApplicationArguments(new String[0]));

        verify(workflowMemberRoleRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue())
                .hasSize(1)
                .first()
                .satisfies(row -> {
                    assertThat(row.getWorkflowType()).isEqualTo("RESEARCH");
                    assertThat(row.getWorkflowId()).isEqualTo("research-dup");
                    assertThat(row.getUserId()).isEqualTo("000301");
                    assertThat(row.getRole()).isEqualTo("HOST");
                });
    }

    private User buildUser(String userId) {
        return User.builder()
                .userId(userId)
                .username("user-" + userId)
                .password("encoded")
                .role("MEMBER")
                .active(true)
                .build();
    }
}
