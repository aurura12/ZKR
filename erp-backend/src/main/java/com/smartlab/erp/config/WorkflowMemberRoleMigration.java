package com.smartlab.erp.config;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProductIdeaDetail;
import com.smartlab.erp.entity.ResearchProjectProfile;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.WorkflowMemberRole;
import com.smartlab.erp.repository.ProductIdeaDetailRepository;
import com.smartlab.erp.repository.ResearchProjectProfileRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.WorkflowMemberRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Configuration
@RequiredArgsConstructor
public class WorkflowMemberRoleMigration {

    private static final String RESEARCH_BLUEPRINT_OWNER = "BLUEPRINT_OWNER";
    private static final String RESEARCH_ARCHITECTURE_OWNER = "ARCHITECTURE_OWNER";
    private static final String RESEARCH_TASK_BREAKDOWN_OWNER = "TASK_BREAKDOWN_OWNER";
    private static final String RESEARCH_EVALUATION_REPORT_OWNER = "EVALUATION_REPORT_OWNER";

    private final SysProjectRepository projectRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final ResearchProjectProfileRepository researchProjectProfileRepository;
    private final ProductIdeaDetailRepository productIdeaDetailRepository;
    private final WorkflowMemberRoleRepository workflowMemberRoleRepository;

    @Bean
    @DependsOn("migrateUserSchema")
    @Order(110)
    ApplicationRunner backfillWorkflowMemberRoles() {
        return args -> {
            List<WorkflowMemberRole> rows = new ArrayList<>();
            rows.addAll(backfillProjectRoles());
            rows.addAll(backfillResearchRoles());
            rows.addAll(backfillProductRoles());

            List<WorkflowMemberRole> uniqueRows = rows.stream().distinct().toList();

            if (!uniqueRows.isEmpty()) {
                workflowMemberRoleRepository.saveAll(uniqueRows);
            }
        };
    }

    private List<WorkflowMemberRole> backfillProjectRoles() {
        List<WorkflowMemberRole> rows = new ArrayList<>();
        for (SysProject project : projectRepository.findAll()) {
            if (project == null || project.getProjectId() == null || project.getFlowType() == null) {
                continue;
            }
            for (SysProjectMember member : projectMemberRepository.findByProjectId(project.getProjectId())) {
                appendRole(rows, project.getFlowType().name(), project.getProjectId(), member == null ? null : member.getUser() == null ? null : member.getUser().getUserId(), member == null ? null : member.getRole());
            }
        }
        return rows;
    }

    private List<WorkflowMemberRole> backfillResearchRoles() {
        List<WorkflowMemberRole> rows = new ArrayList<>();
        for (ResearchProjectProfile profile : researchProjectProfileRepository.findAll()) {
            if (profile == null || profile.getProjectId() == null) {
                continue;
            }
            SysProject project = projectRepository.findById(profile.getProjectId()).orElse(null);
            if (project == null || project.getFlowType() != FlowType.RESEARCH) {
                continue;
            }
            appendRole(rows, FlowType.RESEARCH.name(), project.getProjectId(), profile.getIdeaOwnerUserId(), "RESEARCH");
            appendRole(rows, FlowType.RESEARCH.name(), project.getProjectId(), profile.getHostUserId(), "HOST");
            appendRole(rows, FlowType.RESEARCH.name(), project.getProjectId(), profile.getChiefEngineerUserId(), "CHIEF_ENGINEER");
            appendRole(rows, FlowType.RESEARCH.name(), project.getProjectId(), profile.getBlueprintOwnerUserId(), RESEARCH_BLUEPRINT_OWNER);
            appendRole(rows, FlowType.RESEARCH.name(), project.getProjectId(), profile.getArchitectureOwnerUserId(), RESEARCH_ARCHITECTURE_OWNER);
            appendRole(rows, FlowType.RESEARCH.name(), project.getProjectId(), profile.getTaskBreakdownOwnerUserId(), RESEARCH_TASK_BREAKDOWN_OWNER);
            appendRole(rows, FlowType.RESEARCH.name(), project.getProjectId(), profile.getEvaluationReportOwnerUserId(), RESEARCH_EVALUATION_REPORT_OWNER);
        }
        return rows;
    }

    private List<WorkflowMemberRole> backfillProductRoles() {
        List<WorkflowMemberRole> rows = new ArrayList<>();
        for (ProductIdeaDetail detail : productIdeaDetailRepository.findAll()) {
            if (detail == null || detail.getProjectId() == null) {
                continue;
            }
            SysProject project = projectRepository.findById(detail.getProjectId()).orElse(null);
            if (project == null || project.getFlowType() != FlowType.PRODUCT) {
                continue;
            }
            appendRole(rows, FlowType.PRODUCT.name(), project.getProjectId(), detail.getIdeaOwnerUserId(), "ADMIN");
            appendRole(rows, FlowType.PRODUCT.name(), project.getProjectId(), detail.getPromotionIcUserId(), "PROMOTION_IC");
            appendRole(rows, FlowType.PRODUCT.name(), project.getProjectId(), detail.getDemoEngineeringOwnerUserId(), "DEMO_ENG");
            appendRole(rows, FlowType.PRODUCT.name(), project.getProjectId(), detail.getDemoFileOwnerUserId(), "DEMO_ENG");
            appendRole(rows, FlowType.PRODUCT.name(), project.getProjectId(), detail.getDemoDescriptionOwnerUserId(), "DEMO_ENG");
            appendRole(rows, FlowType.PRODUCT.name(), project.getProjectId(), detail.getDemoFeasibilityOwnerUserId(), "DEMO_ENG");
        }
        return rows;
    }

    private void appendRole(List<WorkflowMemberRole> rows, String workflowType, String workflowId, String userId, String role) {
        if (isBlank(workflowType) || isBlank(workflowId) || isBlank(userId) || isBlank(role)) {
            return;
        }
        boolean exists = rows.stream().anyMatch(item -> Objects.equals(item.getWorkflowType(), workflowType)
                && Objects.equals(item.getWorkflowId(), workflowId)
                && Objects.equals(item.getUserId(), userId)
                && Objects.equals(item.getRole(), role));
        if (exists || workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowIdAndUserIdAndRole(workflowType, workflowId, userId, role).isPresent()) {
            return;
        }
        rows.add(WorkflowMemberRole.builder()
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
