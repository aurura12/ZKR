package com.smartlab.erp.service;

import com.smartlab.erp.dto.WorkflowMemberRoleDTO;
import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProductIdeaDetail;
import com.smartlab.erp.entity.ResearchProjectProfile;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class WorkflowMemberRoleService {

    private final WorkflowMemberRoleRepository workflowMemberRoleRepository;
    private final SysProjectRepository projectRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final ProductIdeaDetailRepository productIdeaDetailRepository;
    private final ResearchProjectProfileRepository researchProjectProfileRepository;
    private final UserRepository userRepository;

    public List<WorkflowMemberRoleDTO> getWorkflowMemberRoles(String workflowType, String workflowId) {
        if (isBlank(workflowType) || isBlank(workflowId)) {
            return List.of();
        }

        List<WorkflowMemberRole> newRows = workflowMemberRoleRepository.findByWorkflowTypeAndWorkflowId(workflowType, workflowId);
        if (newRows != null && !newRows.isEmpty()) {
            return enrich(newRows, workflowType, workflowId);
        }

        return switch (normalizeWorkflowType(workflowType)) {
            case "PROJECT" -> legacyProjectRows(workflowId);
            case "PRODUCT" -> legacyProductRows(workflowId);
            case "RESEARCH" -> legacyResearchRows(workflowId);
            default -> List.of();
        };
    }

    public List<WorkflowMemberRoleDTO> getWorkflowRoleCandidates(String workflowType) {
        if (isBlank(workflowType)) {
            return List.of();
        }

        String normalizedWorkflowType = normalizeWorkflowType(workflowType);
        List<WorkflowMemberRole> newRows = workflowMemberRoleRepository.findByWorkflowType(normalizedWorkflowType);
        if (newRows != null && !newRows.isEmpty()) {
            return enrich(newRows, normalizedWorkflowType, null);
        }

        if ("RESEARCH".equals(normalizedWorkflowType)) {
            List<WorkflowMemberRoleDTO> rows = new ArrayList<>();
            projectRepository.findAll().stream()
                    .filter(project -> project != null && project.getFlowType() == FlowType.RESEARCH)
                    .forEach(project -> rows.addAll(legacyResearchRows(project.getProjectId())));
            return rows;
        }

        return List.of();
    }

    private List<WorkflowMemberRoleDTO> legacyProjectRows(String workflowId) {
        List<SysProjectMember> members = projectMemberRepository.findByProjectIdWithUser(workflowId);
        return enrichMembers(FlowType.PROJECT.name(), workflowId, members, SysProjectMember::getRole);
    }

    private List<WorkflowMemberRoleDTO> legacyProductRows(String workflowId) {
        List<WorkflowMemberRoleDTO> rows = new ArrayList<>();
        projectRepository.findById(workflowId).ifPresent(project -> {
            ProductIdeaDetail detail = productIdeaDetailRepository.findByProjectId(workflowId).orElse(null);
            appendLegacyProductRole(rows, workflowId, project.getManager(), "ADMIN");
            if (detail != null) {
                appendLegacyProductRole(rows, workflowId, resolveUser(detail.getIdeaOwnerUserId()), "ADMIN");
                appendLegacyProductRole(rows, workflowId, resolveUser(detail.getPromotionIcUserId()), "PROMOTION_IC");
                appendLegacyProductRole(rows, workflowId, resolveUser(detail.getDemoEngineeringOwnerUserId()), "DEMO_ENG");
                appendLegacyProductRole(rows, workflowId, resolveUser(detail.getDemoFileOwnerUserId()), "DEMO_ENG");
                appendLegacyProductRole(rows, workflowId, resolveUser(detail.getDemoDescriptionOwnerUserId()), "DEMO_ENG");
                appendLegacyProductRole(rows, workflowId, resolveUser(detail.getDemoFeasibilityOwnerUserId()), "DEMO_ENG");
            }
            projectMemberRepository.findByProjectIdWithUser(workflowId).forEach(member -> appendLegacyProductRole(rows, workflowId, member.getUser(), member.getRole()));
        });
        return rows;
    }

    private List<WorkflowMemberRoleDTO> legacyResearchRows(String workflowId) {
        List<WorkflowMemberRoleDTO> rows = new ArrayList<>();
        projectRepository.findById(workflowId).ifPresent(project -> {
            ResearchProjectProfile profile = researchProjectProfileRepository.findByProjectId(workflowId).orElse(null);
            if (profile != null) {
                appendLegacyResearchRole(rows, workflowId, resolveUser(profile.getIdeaOwnerUserId()), "RESEARCH");
                appendLegacyResearchRole(rows, workflowId, resolveUser(profile.getHostUserId()), "HOST");
                appendLegacyResearchRole(rows, workflowId, resolveUser(profile.getChiefEngineerUserId()), "CHIEF_ENGINEER");
                appendLegacyResearchRole(rows, workflowId, resolveUser(profile.getBlueprintOwnerUserId()), "BLUEPRINT_OWNER");
                appendLegacyResearchRole(rows, workflowId, resolveUser(profile.getArchitectureOwnerUserId()), "ARCHITECTURE_OWNER");
                appendLegacyResearchRole(rows, workflowId, resolveUser(profile.getTaskBreakdownOwnerUserId()), "TASK_BREAKDOWN_OWNER");
                appendLegacyResearchRole(rows, workflowId, resolveUser(profile.getEvaluationReportOwnerUserId()), "EVALUATION_REPORT_OWNER");
            }
            projectMemberRepository.findByProjectIdWithUser(workflowId).forEach(member -> appendLegacyResearchRole(rows, workflowId, member.getUser(), member.getRole()));
        });
        return rows;
    }

    private List<WorkflowMemberRoleDTO> enrich(List<WorkflowMemberRole> rows, String workflowType, String workflowId) {
        Map<String, WorkflowMemberRoleDTO> result = new LinkedHashMap<>();
        for (WorkflowMemberRole row : rows) {
            if (row == null || isBlank(row.getUserId()) || isBlank(row.getRole())) {
                continue;
            }
            User user = resolveUser(row.getUserId());
            WorkflowMemberRoleDTO dto = WorkflowMemberRoleDTO.builder()
                    .workflowType(workflowType)
                    .workflowId(workflowId)
                    .userId(row.getUserId())
                    .role(row.getRole())
                    .name(user == null ? null : nameOf(user))
                    .username(user == null ? null : user.getUsername())
                    .avatar(user == null ? null : user.getAvatar())
                    .hiddenAvatar(user == null ? null : user.getHiddenAvatar())
                    .build();
            result.put(dtoKey(dto), dto);
        }
        return new ArrayList<>(result.values());
    }

    public List<WorkflowMemberRoleDTO> getResearchRoleCandidates() {
        return getWorkflowRoleCandidates(FlowType.RESEARCH.name());
    }

    private List<WorkflowMemberRoleDTO> enrichMembers(String workflowType,
                                                     String workflowId,
                                                     List<SysProjectMember> members,
                                                     Function<SysProjectMember, String> roleExtractor) {
        List<WorkflowMemberRoleDTO> rows = new ArrayList<>();
        for (SysProjectMember member : members) {
            if (member == null || member.getUser() == null || isBlank(member.getUser().getUserId())) {
                continue;
            }
            String role = roleExtractor.apply(member);
            if (isBlank(role)) {
                continue;
            }
            rows.add(WorkflowMemberRoleDTO.builder()
                    .workflowType(workflowType)
                    .workflowId(workflowId)
                    .userId(member.getUser().getUserId())
                    .role(role)
                    .name(nameOf(member.getUser()))
                    .username(member.getUser().getUsername())
                    .avatar(member.getUser().getAvatar())
                    .hiddenAvatar(member.getUser().getHiddenAvatar())
                    .build());
        }
        return rows;
    }

    private void appendLegacyProductRole(List<WorkflowMemberRoleDTO> rows, String workflowId, User user, String role) {
        appendLegacyRole(rows, FlowType.PRODUCT.name(), workflowId, user, role);
    }

    private void appendLegacyResearchRole(List<WorkflowMemberRoleDTO> rows, String workflowId, User user, String role) {
        appendLegacyRole(rows, FlowType.RESEARCH.name(), workflowId, user, role);
    }

    private void appendLegacyRole(List<WorkflowMemberRoleDTO> rows, String workflowType, String workflowId, User user, String role) {
        if (user == null || isBlank(user.getUserId()) || isBlank(role)) {
            return;
        }
        rows.add(WorkflowMemberRoleDTO.builder()
                .workflowType(workflowType)
                .workflowId(workflowId)
                .userId(user.getUserId())
                .role(role)
                .name(nameOf(user))
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .hiddenAvatar(user.getHiddenAvatar())
                .build());
    }

    private User resolveUser(String userId) {
        if (isBlank(userId)) {
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }

    private String nameOf(User user) {
        if (user == null) {
            return null;
        }
        return user.getName() != null ? user.getName() : user.getUsername();
    }

    private String dtoKey(WorkflowMemberRoleDTO dto) {
        return dto.getWorkflowType() + "|" + dto.getWorkflowId() + "|" + dto.getUserId() + "|" + dto.getRole();
    }

    private String normalizeWorkflowType(String workflowType) {
        return workflowType == null ? "" : workflowType.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
