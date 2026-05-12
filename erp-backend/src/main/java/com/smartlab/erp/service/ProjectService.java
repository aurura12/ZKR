package com.smartlab.erp.service;

import com.smartlab.erp.dto.AdjustProjectCostRequest;
import com.smartlab.erp.dto.CreateProjectRequest;
import com.smartlab.erp.dto.FinanceDashboardResponse;
import com.smartlab.erp.dto.ManagedProjectsSummaryResponse;
import com.smartlab.erp.dto.ProjectDetailResponse;
import com.smartlab.erp.dto.ProjectDynamicInfoUpdateRequest;
import com.smartlab.erp.dto.ProjectInitiateRequestDTO;
import com.smartlab.erp.dto.ReviewExpenseRequest;
import com.smartlab.erp.dto.SubmitProjectExpenseRequest;
import com.smartlab.erp.dto.ProjectBuildTeamRequestDTO;
import com.smartlab.erp.dto.ProjectSubtaskRequest;
import com.smartlab.erp.dto.ProjectSubtaskResponse;
import com.smartlab.erp.dto.MemberDTO;
import com.smartlab.erp.entity.*;
import com.smartlab.erp.enums.ProjectCostAdjustmentType;
import com.smartlab.erp.enums.ProjectExpenseStatus;
import com.smartlab.erp.enums.ProjectExpenseType;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.enums.ProjectTierEnum;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.util.AuthUtils;
import com.smartlab.erp.repository.*;
import com.smartlab.erp.finance.repository.FinanceCostSummaryRepository;
import com.smartlab.erp.finance.repository.FinanceCostBatchRepository;
import com.smartlab.erp.finance.entity.FinanceCostSummary;
import com.smartlab.erp.finance.entity.FinanceCostBatch;
import com.smartlab.erp.security.RbacService;
import com.smartlab.erp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private static final String RESEARCH_BLUEPRINT_DOC = "RESEARCH_BLUEPRINT_DOC";
    private static final String RESEARCH_ARCHITECTURE_DOC = "RESEARCH_ARCHITECTURE_DOC";
    private static final String RESEARCH_TASK_BREAKDOWN_DOC = "RESEARCH_TASK_BREAKDOWN_DOC";
    private static final String RESEARCH_EVALUATION_REPORT = "RESEARCH_EVALUATION_REPORT";

    private static final String IMPLEMENTATION_STATUS_TAG = "【实施状态】:";
    private static final Pattern IMPLEMENTATION_STATUS_PATTERN = Pattern.compile("【实施状态】:[^|]*");
    private static final String PROJECT_TIER_TAG = "【评级】:";
    private static final Pattern PROJECT_TIER_PATTERN = Pattern.compile("【评级】:[^|]*");
    private static final String FEASIBILITY_REPORT_STATUS_TAG = "【可行性报告】:";
    private static final Pattern FEASIBILITY_REPORT_STATUS_PATTERN = Pattern.compile("【可行性报告】:[^|]*|【可行性报告\\s*URL】:[^|]*");
    private static final String FEASIBILITY_REPORT_ASSET_CATEGORY = "FEASIBILITY_REPORT";
    private static final String INITIATION_ATTACHMENT_ASSET_CATEGORY = "INITIATION_ATTACHMENT";
    private static final List<String> PROJECT_SCOPED_TABLES = List.of(
            "execution_archive_folder",
            "execution_file",
            "finance_clearing_sheet",
            "finance_cost_entry",
            "finance_cost_summary",
            "finance_dividend_sheet",
            "finance_expense_submission",
            "finance_venture_equity",
            "finance_venture_profile",
            "finance_wallet_transaction",
            "internal_message",
            "product_idea_detail",
            "project_asset",
            "project_budget_history",
            "project_chat_message",
            "project_execution_plan",
            "project_git_repository",
            "project_member_participation_history",
            "project_member_schedule",
            "project_milestone",
            "project_subtask",
            "research_project_profile",
            "sys_project_member"
    );

    private final SysProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final ProjectMilestoneRepository milestoneRepository;
    private final ProjectAssetRepository assetRepository;
    private final ProjectSubtaskRepository projectSubtaskRepository;
    private final ProjectExecutionPlanRepository executionPlanRepository;
    private final ProjectMemberScheduleRepository projectMemberScheduleRepository;
    private final ProjectMemberParticipationService projectMemberParticipationService;
    private final ProductIdeaDetailRepository productIdeaDetailRepository;
    private final ResearchProjectProfileRepository researchProjectProfileRepository;
    private final StateMachineService stateMachineService;
    private final ProjectFinancialMetricsService projectFinancialMetricsService;
    private final RbacService rbacService;
    private final InternalMessageService internalMessageService;
    private final WorkflowMemberRoleSyncService workflowMemberRoleSyncService;
    private final FinanceCostSummaryRepository costSummaryRepository;
    private final FinanceCostBatchRepository costBatchRepository;
    private final ProjectCostAdjustmentRepository costAdjustmentRepository;
    private final ProjectExpenseRepository expenseRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${auth.admin-usernames:Zhangqi,guojianwen,jiaomiao}")
    private String adminUsernamesConfig;

    private Set<String> getAdminUsernames() {
        return Set.of(adminUsernamesConfig.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MM/dd").withZone(ZoneId.systemDefault());

    /**
     * ✅ API 需求 1：商务发起项目 (Initiation)
     */
    @Transactional
    public SysProject initiateProject(ProjectInitiateRequestDTO request) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        String currentUserRole = currentUser.getRole() != null
                ? currentUser.getRole().trim().toUpperCase()
                : "";
        if (!"BUSINESS".equals(currentUserRole)) {
            throw new PermissionDeniedException("权限不足：仅商务角色可发起项目");
        }

        String currentUserId = currentUser.getId();

        // 1. 查找当前 BD 用户
        User bdUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException("当前BD用户不存在"));

        // 2. 查找指定的数据工程师 (兼容 Long 到 String 的转换，解决 ID 类型报错)
        String dataEngineerId = String.valueOf(request.getDataEngineerId());
        User dataEngineer = userRepository.findById(dataEngineerId)
                .orElseThrow(() -> new BusinessException("指定的数据工程师不存在"));
        String normalizedDataRole = dataEngineer.getRole() == null ? "" : dataEngineer.getRole().trim().toUpperCase();
        if (!Set.of("DATA", "DATA_ENGINEER").contains(normalizedDataRole)) {
            throw new BusinessException("指定用户不是数据工程师角色");
        }
        if (dataEngineer.getAccountDomain() != AccountDomain.ERP) {
            throw new BusinessException("指定数据工程师必须是ERP域账号");
        }

        if (request.getProjectType() == null) {
            throw new BusinessException("项目行业不能为空，仅支持：工业/军工/医药/AI for Science/群体智能/自用");
        }
        if (!Set.of(
                ProjectType.INDUSTRIAL,
                ProjectType.MILITARY,
                ProjectType.MEDICAL,
                ProjectType.AI_FOR_SCIENCE,
                ProjectType.SWARM_INTEL,
                ProjectType.SELF_USE
        ).contains(request.getProjectType())) {
            throw new BusinessException("项目行业无效，仅支持：工业/军工/医药/AI for Science/群体智能/自用");
        }

        // 3. 严格遵守无侵入原则 (Sidecar模式兼容)
        // 核心规则：将原“预算”字段修改为“预计项目收入金额”。
        // 将评级等其他新字段暂时压缩为扩展字符串放入 description，避免污染 Entity
        String extensionInfo = String.format("【评级】:%s | 【可行性报告】:%s | 【实施状态】:%s",
                request.getProjectTier() != null ? request.getProjectTier() : "未定级",
                isPresent(request.getFeasibilityReportUrl()) ? "已上传" : "未上传",
                "未设置");

        SysProject project = SysProject.builder()
                .projectId(UUID.randomUUID().toString())
                .name(request.getProjectName())
                .description(extensionInfo)
                .manager(dataEngineer)
                .projectType(request.getProjectType())
                .flowType(FlowType.PROJECT)
                .projectStatus(ProjectStatus.INITIATED)
                .budget(request.getEstimatedRevenue() != null ? request.getEstimatedRevenue() : BigDecimal.ZERO)
                .cost(BigDecimal.ZERO)
                .projectTier(null)
                .techStack("")
                .repoUrl("")
                .deployUrl("")
                .build();

        SysProject savedProject = projectRepository.save(project);

        // 4. 将成员写入关联表 (统一使用 String 角色标签，防止 Enum 不匹配)
        saveMember(savedProject, bdUser, "BD", 0);
        saveMember(savedProject, dataEngineer, "DATA_ENGINEER", 0);
        internalMessageService.sendMessage(
                dataEngineer.getUserId(),
                "PROJECT_JOINED",
                "你已加入项目",
                "你被选中加入项目《" + savedProject.getName() + "》",
                savedProject.getProjectId()
        );

        return savedProject;
    }

    /**
     * ✅ API 需求 2：数据工程师组建团队 (Team Formation)
     */
    @Transactional
    public SysProject buildTeam(String projectId, ProjectBuildTeamRequestDTO request) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        String currentUserId = currentUser.getId();

        // 1. 权限拦截：仅该项目的数据工程师可调用
        SysProjectMember dataEngineerMember = projectMemberRepository.findByProjectIdAndUserUserId(projectId, currentUserId)
                .orElseThrow(() -> new PermissionDeniedException("您不是该项目成员"));

        if (!"DATA_ENGINEER".equals(dataEngineerMember.getRole()) && !"MANAGER".equals(dataEngineerMember.getRole())) {
            throw new PermissionDeniedException("权限不足：仅数据工程师或Manager可组建团队");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));

        // 2. 指定 Manager 身份校验
        String targetManagerId = String.valueOf(request.getManagerUserId());
        User managerToAssign = userRepository.findById(targetManagerId)
                .orElseThrow(() -> new BusinessException("指定的Manager用户不存在"));

        SysProjectMember targetManagerMember = projectMemberRepository.findByProjectIdAndUserUserId(projectId, targetManagerId)
                .orElseThrow(() -> new BusinessException("项目经理必须从当前项目的商务或数据工程师中选择"));
        String managerCandidateRole = targetManagerMember.getRole() != null
                ? targetManagerMember.getRole().toUpperCase()
                : "";
        if (!Set.of("BD", "BUSINESS", "DATA", "DATA_ENGINEER").contains(managerCandidateRole)) {
            throw new BusinessException("项目经理必须从当前项目的商务或数据工程师中选择");
        }

        List<ProjectMemberRatioInput> memberInputs = request.getTeamMembers() == null ? List.of() : request.getTeamMembers().stream()
                .map(memberDTO -> new ProjectMemberRatioInput(
                        String.valueOf(memberDTO.getUserId()),
                        memberDTO.getRole() == null ? null : memberDTO.getRole().toString(),
                        memberDTO.getWeight()))
                .toList();
        Set<String> existingMemberIds = projectMemberRepository.findByProjectId(projectId).stream()
                .map(member -> member.getUser() == null ? null : member.getUser().getUserId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        applyProjectResponsibilityAllocation(project, targetManagerId, request.getManagerWeight(), request.getManagerExecutionWeight(), memberInputs);

        // 3. 将入参中的团队成员批量写入关联表
        if (request.getTeamMembers() != null && !request.getTeamMembers().isEmpty()) {
            for (MemberDTO memberDTO : request.getTeamMembers()) {
                String memberUserId = String.valueOf(memberDTO.getUserId());
                User memberUser = userRepository.findById(memberUserId)
                        .orElseThrow(() -> new BusinessException("团队成员不存在"));
                if (!existingMemberIds.contains(memberUserId) && !memberUser.getUserId().equals(currentUserId)) {
                    internalMessageService.sendMessage(
                            memberUser.getUserId(),
                            "PROJECT_JOINED",
                            "你已加入项目",
                            "你被选中加入项目《" + project.getName() + "》",
                            projectId
                    );
                }
            }
        }

        // 更新项目的 manager
        project.setManager(managerToAssign);

        // 5. 组队完成后保持 INITIATED，若此前已上传可行性报告则自动推进
        project.setProjectStatus(ProjectStatus.INITIATED);
        ensureExecutionScheduleSlots(projectId);
        SysProject saved = projectRepository.save(project);
        tryAutoAdvanceStatus(saved);
        return saved;
    }

    private void ensureExecutionScheduleSlots(String projectId) {
        List<SysProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        for (SysProjectMember member : members) {
            String userId = member.getUser() == null ? null : member.getUser().getUserId();
            if (userId == null || userId.isBlank()) {
                continue;
            }
            projectMemberScheduleRepository.findByProjectIdAndUserId(projectId, userId)
                    .orElseGet(() -> projectMemberScheduleRepository.save(ProjectMemberSchedule.builder()
                            .projectId(projectId)
                            .userId(userId)
                            .completed(false)
                            .managerConfirmed(false)
                            .build()));
        }
    }

    /**
     * 🟢 核心基础方法：保存项目成员及权重
     */
    private boolean saveMember(SysProject project, User user, String role, Integer weight) {
        return upsertMember(project, user, role, weight, null);
    }

    private boolean upsertMember(SysProject project, User user, String role, Integer weight, Integer managerWeight) {
        Optional<SysProjectMember> existing = projectMemberRepository.findByProjectIdAndUserUserId(project.getProjectId(), user.getUserId());
        SysProjectMember member = existing.orElseGet(() -> SysProjectMember.builder()
                .projectId(project.getProjectId())
                .user(user)
                .build());
        member.setRole(resolveProjectMemberRole(existing.map(SysProjectMember::getRole).orElse(null), role));
        if (weight != null) {
            member.setWeight(sanitizeRatio(weight));
        }
        if (managerWeight != null) {
            member.setManagerWeight(sanitizeRatio(managerWeight));
        }
        projectMemberRepository.save(member);
        workflowMemberRoleSyncService.sync(project.getFlowType().name(), project.getProjectId(), user.getUserId(), member.getRole());
        projectMemberParticipationService.recordJoin(project.getProjectId(), user, member.getJoinedAt());
        return existing.isEmpty();
    }

    private Integer sanitizeRatio(Integer ratio) {
        if (ratio == null) {
            return 0;
        }
        return Math.max(ratio, 0);
    }

    private String resolveProjectMemberRole(String existingRole, String requestedRole) {
        String normalizedExistingRole = normalizeProjectMemberRole(existingRole);
        String normalizedRequestedRole = normalizeProjectMemberRole(requestedRole);
        if (normalizedRequestedRole.isBlank()) {
            return normalizedExistingRole;
        }
        if ("DATA_ENGINEER".equals(normalizedExistingRole) && "DATA".equals(normalizedRequestedRole)) {
            return "DATA_ENGINEER";
        }
        return normalizedRequestedRole;
    }

    private String normalizeProjectMemberRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if (normalized.isBlank()) {
            return "";
        }
        if ("ALGO".equals(normalized)) {
            return "ALGORITHM";
        }
        if ("BUSNESS".equals(normalized)) {
            return "BUSINESS";
        }
        return normalized;
    }

    public String normalizeMemberRole(String role) {
        return normalizeProjectMemberRole(role);
    }

    public boolean isExecutionMemberRole(String role) {
        return Set.of("DEV", "ALGORITHM", "RESEARCH", "MEMBER", "DEMO_ENG", "DATA", "DATA_ENGINEER")
                .contains(normalizeProjectMemberRole(role));
    }

    public void applyProjectResponsibilityAllocation(String projectId,
                                                     String managerUserId,
                                                     Integer managerWeight,
                                                     Integer managerExecutionWeight,
                                                     List<ProjectMemberRatioInput> memberInputs) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));
        applyProjectResponsibilityAllocation(project, managerUserId, managerWeight, managerExecutionWeight, memberInputs);
    }

    public void applyProjectResponsibilityAllocation(SysProject project,
                                                     String managerUserId,
                                                     Integer managerWeight,
                                                     Integer managerExecutionWeight,
                                                     List<ProjectMemberRatioInput> memberInputs) {
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        String normalizedManagerUserId = managerUserId == null ? "" : managerUserId.trim();
        if (normalizedManagerUserId.isBlank()) {
            throw new BusinessException("请先指定项目经理");
        }

        SysProjectMember managerMember = projectMemberRepository.findByProjectIdAndUserUserId(project.getProjectId(), normalizedManagerUserId)
                .orElseThrow(() -> new BusinessException("项目经理必须从当前项目成员中选择"));
        String managerCandidateRole = normalizeProjectMemberRole(managerMember.getRole());
        if (!Set.of("BD", "BUSINESS", "DATA", "DATA_ENGINEER").contains(managerCandidateRole)) {
            throw new BusinessException("项目经理必须从当前项目的商务或数据工程师中选择");
        }

        // 先将所有成员的 manager_weight 清零，防止替换 Manager 时旧值残留
        List<SysProjectMember> allMembers = projectMemberRepository.findByProjectId(project.getProjectId());
        for (SysProjectMember member : allMembers) {
            if (member.getManagerWeight() != null && member.getManagerWeight() != 0) {
                member.setManagerWeight(0);
                projectMemberRepository.save(member);
            }
        }

        List<ProjectMemberRatioInput> normalizedInputs = memberInputs == null
                ? List.of()
                : memberInputs.stream()
                .filter(Objects::nonNull)
                .filter(input -> input.getUserId() != null && !input.getUserId().trim().isBlank())
                .toList();

        int normalizedManagerWeight = sanitizeRatio(managerWeight);
        int normalizedManagerExecutionWeight = sanitizeRatio(managerExecutionWeight);
        int memberWeightSum = normalizedInputs.stream()
                .mapToInt(input -> sanitizeRatio(input.getWeight()))
                .sum();
        int expectedTotal = normalizedManagerWeight + memberWeightSum
                + (Set.of("DATA", "DATA_ENGINEER").contains(managerCandidateRole) ? normalizedManagerExecutionWeight : 0);
        if (expectedTotal != 100) {
            throw new BusinessException("组队权责比总和必须为100");
        }

        boolean hasExecutionMember = normalizedInputs.stream()
                .anyMatch(input -> isExecutionMemberRole(input.getRole()) && sanitizeRatio(input.getWeight()) > 0)
                || (Set.of("DATA", "DATA_ENGINEER").contains(managerCandidateRole) && normalizedManagerExecutionWeight > 0);
        if (!hasExecutionMember) {
            throw new BusinessException("至少需要为一名开发/算法/数据成员分配大于0的执行权责比");
        }

        for (ProjectMemberRatioInput input : normalizedInputs) {
            String memberUserId = input.getUserId().trim();
            User memberUser = userRepository.findById(memberUserId)
                    .orElseThrow(() -> new BusinessException("团队成员不存在"));
            String roleStr = normalizeProjectMemberRole(input.getRole());
            if (roleStr.isBlank()) {
                roleStr = "MEMBER";
            }
            upsertMember(project, memberUser, roleStr, sanitizeRatio(input.getWeight()), null);
        }

        managerMember.setManagerWeight(normalizedManagerWeight);
        if (Set.of("DATA", "DATA_ENGINEER").contains(managerCandidateRole)) {
            managerMember.setWeight(normalizedManagerExecutionWeight);
        } else {
            managerMember.setWeight(sanitizeRatio(managerMember.getWeight()));
        }
        projectMemberRepository.save(managerMember);
        workflowMemberRoleSyncService.sync(project.getFlowType().name(), project.getProjectId(), managerMember.getUser().getUserId(), managerMember.getRole());
        projectMemberParticipationService.recordJoin(project.getProjectId(), managerMember.getUser(), managerMember.getJoinedAt());
    }

    public ProjectTierEnum resolveProjectTier(ProjectExecutionPlan executionPlan, SysProject project) {
        if (executionPlan != null && executionPlan.getProjectTier() != null) {
            return executionPlan.getProjectTier();
        }
        if (project != null && project.getProjectTier() != null) {
            return project.getProjectTier();
        }
        String legacy = readTaggedDescriptionValue(project == null ? null : project.getDescription(), "评级");
        if (legacy.isBlank() || "未定级".equals(legacy)) {
            return null;
        }
        try {
            return ProjectTierEnum.valueOf(legacy.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String readTaggedDescriptionValue(String description, String tag) {
        String source = description == null ? "" : description;
        String marker = "【" + tag + "】:";
        int start = source.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int contentStart = start + marker.length();
        int end = source.indexOf('|', contentStart);
        String value = end < 0 ? source.substring(contentStart) : source.substring(contentStart, end);
        return value == null ? "" : value.trim();
    }

    private String buildDescriptionWithCurrentTags(String rawDescription, ProjectTierEnum projectTier, boolean hasFeasibilityReport, String implementationStatus) {
        String baseDescription = rawDescription == null ? "" : rawDescription;
        baseDescription = PROJECT_TIER_PATTERN.matcher(baseDescription).replaceAll("");
        baseDescription = FEASIBILITY_REPORT_STATUS_PATTERN.matcher(baseDescription).replaceAll("");
        baseDescription = IMPLEMENTATION_STATUS_PATTERN.matcher(baseDescription).replaceAll("");
        baseDescription = baseDescription.replaceAll("\\|\\s*\\|", "|").replaceAll("^\\|\\s*", "").replaceAll("\\s*\\|$", "").trim();

        StringBuilder result = new StringBuilder();
        if (!baseDescription.isBlank()) {
            result.append(baseDescription);
        }

        if (projectTier != null) {
            if (result.length() > 0) result.append(" | ");
            result.append(PROJECT_TIER_TAG).append(projectTier.name());
        }

        if (hasFeasibilityReport) {
            if (result.length() > 0) result.append(" | ");
            result.append(FEASIBILITY_REPORT_STATUS_TAG).append("已上传");
        }

        if (implementationStatus != null && !implementationStatus.isBlank() && !"未设置".equals(implementationStatus.trim())) {
            if (result.length() > 0) result.append(" | ");
            result.append(IMPLEMENTATION_STATUS_TAG).append(implementationStatus);
        }

        return result.toString().trim();
    }

    public static class ProjectMemberRatioInput {
        private final String userId;
        private final String role;
        private final Integer weight;

        public ProjectMemberRatioInput(String userId, String role, Integer weight) {
            this.userId = userId;
            this.role = role;
            this.weight = weight;
        }

        public String getUserId() {
            return userId;
        }

        public String getRole() {
            return role;
        }

        public Integer getWeight() {
            return weight;
        }
    }

    /**
     * ✅ 遗留创建项目代码 (保持不变)
     */
    @Transactional
    public SysProject createProject(CreateProjectRequest request, String currentUserId) {
        String finalManagerId = (request.getManagerId() != null && !request.getManagerId().isEmpty())
                ? request.getManagerId()
                : currentUserId;

        User manager = userRepository.findById(finalManagerId)
                .orElseThrow(() -> new RuntimeException("指定的负责人不存在"));

        String newProjectId = UUID.randomUUID().toString();
        FlowType flowType = request.getFlowType() != null ? request.getFlowType() : FlowType.PROJECT;
        ProjectStatus projectStatus = (flowType == FlowType.PROJECT) ? ProjectStatus.LEAD : ProjectStatus.INITIATED;
        // 兼容 ProductStatus 的状态
        ProductStatus productStatus;
        try {
            productStatus = (flowType == FlowType.PRODUCT) ? ProductStatus.valueOf("IDEA") : ProductStatus.valueOf("SHELVED");
        } catch (Exception e) {
            productStatus = null; // 兜底防止枚举尚未更新
        }

        SysProject project = SysProject.builder()
                .projectId(newProjectId)
                .name(request.getName())
                .description(request.getDescription())
                .manager(manager)
                .projectType(request.getProjectType() != null ? request.getProjectType() : ProjectType.BUSINESS)
                .flowType(flowType)
                .projectStatus(projectStatus)
                .productStatus(productStatus)
                .budget(request.getBudget() != null ? BigDecimal.valueOf(request.getBudget()) : BigDecimal.ZERO)
                .cost(BigDecimal.ZERO)
                .techStack("")
                .repoUrl("")
                .deployUrl("")
                .build();

        SysProject savedProject = projectRepository.save(project);

        try {
            saveMember(savedProject, manager, "ADMIN", 0);
            if (request.getMembers() != null && !request.getMembers().isEmpty()) {
                for (CreateProjectRequest.MemberReq req : request.getMembers()) {
                    if (req.getUserId().equals(manager.getUserId())) continue;
                    userRepository.findById(req.getUserId()).ifPresent(user -> {
                        saveMember(savedProject, user, "MEMBER", req.getWeight());
                    });
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("项目成员保存失败: " + e.getMessage());
        }
        return savedProject;
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProjectDetail(String projectId, UserPrincipal currentUser) {
        String userId = currentUser == null ? null : currentUser.getId();
        SysProject project = (currentUser != null && isAdminRole(currentUser.getRole(), currentUser.getUsername()))
                ? projectRepository.findById(projectId)
                    .orElseThrow(() -> new PermissionDeniedException("项目不存在或无权限访问"))
                : projectRepository.findProjectByIdAndUser(projectId, userId)
                .orElseThrow(() -> new PermissionDeniedException("项目不存在或无权限访问"));

        List<ProjectMilestone> dbMilestones = milestoneRepository.findByProjectProjectIdOrderByDueDateAsc(projectId);
        List<ProjectDetailResponse.MilestoneInfo> milestoneInfos = dbMilestones.stream()
                .map(m -> ProjectDetailResponse.MilestoneInfo.builder()
                        .title(m.getTitle())
                        .date(m.getDueDate() != null ? m.getDueDate().toString() : "")
                        .status(m.getStatus().name().toLowerCase())
                        .build())
                .collect(Collectors.toList());

        List<SysProjectMember> dbMembers = projectMemberRepository.findByProjectIdWithUser(projectId);
        List<ProjectDetailResponse.MemberInfo> memberInfos = dbMembers.stream()
                .map(m -> ProjectDetailResponse.MemberInfo.builder()
                        .userId(m.getUser().getUserId())
                        .name(m.getUser().getName() != null ? m.getUser().getName() : m.getUser().getUsername())
                        .avatar(m.getUser().getAvatar())
                        .hiddenAvatar(m.getUser().getHiddenAvatar())
                        .role(m.getRole())
                        .executionResponsibilityRatio(m.getWeight())
                        .managerResponsibilityRatio(m.getManagerWeight())
                        .build())
                .collect(Collectors.toList());

        List<ProjectAsset> dbAssets = assetRepository.findByProjectProjectIdOrderByUploadedAtDesc(projectId);
        List<ProjectSubtask> dbSubtasks = projectSubtaskRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId);
        ProjectExecutionPlan executionPlan = executionPlanRepository.findByProjectId(projectId).orElse(null);
        ProductIdeaDetail productIdeaDetail = productIdeaDetailRepository.findByProjectId(projectId).orElse(null);
        ResearchProjectProfile researchProfile = researchProjectProfileRepository.findByProjectId(projectId).orElse(null);
        Optional<ProjectAsset> feasibilityReport = dbAssets.stream()
                .filter(a -> "FEASIBILITY_REPORT".equalsIgnoreCase(a.getAssetCategory()))
                .max(Comparator.comparing(ProjectAsset::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<ProjectDetailResponse.UploadInfo> uploadInfos = dbAssets.stream()
                .map(a -> ProjectDetailResponse.UploadInfo.builder()
                        .id(a.getId())
                        .name(a.getFileName())
                        .type(a.getFileType())
                        .user(a.getUploaderName())
                        .time(a.getUploadedAt() != null ? DATE_FORMATTER.format(a.getUploadedAt()) : "Just now")
                        .url(buildProjectAssetDownloadUrl(projectId, a.getId()))
                        .category(a.getAssetCategory())
                        .build())
                .collect(Collectors.toList());
        List<ProjectDetailResponse.ProjectSubtaskInfo> subtaskInfos = dbSubtasks.stream()
                .map(task -> ProjectDetailResponse.ProjectSubtaskInfo.builder()
                        .id(task.getId())
                        .title(task.getTitle())
                        .description(task.getDescription())
                        .assigneeUserId(task.getAssigneeUserId())
                        .assigneeName(task.getAssigneeName())
                        .sortOrder(task.getSortOrder())
                        .completed(Boolean.TRUE.equals(task.getCompleted()))
                        .completedAt(task.getCompletedAt() != null ? DATE_FORMATTER.format(task.getCompletedAt()) : null)
                        .build())
                .collect(Collectors.toList());

        String managerId = project.getManager() != null ? project.getManager().getUserId() : "UNKNOWN";
        String statusValue;
        if (project.getFlowType() == FlowType.PROJECT) {
            statusValue = project.getProjectStatus() != null ? project.getProjectStatus().getStageName() : ProjectStatus.LEAD.getStageName();
        } else if (project.getFlowType() == FlowType.PRODUCT) {
            statusValue = project.getProductStatus() != null ? project.getProductStatus().name() : "IDEA";
        } else {
            statusValue = project.getResearchStatus() != null ? project.getResearchStatus().name() : "INIT";
        }

        ProjectTierEnum resolvedProjectTier = resolveProjectTier(executionPlan, project);
        boolean hasFeasibilityReport = hasFeasibilityReport(project);
        String implementationStatus = readTaggedDescriptionValue(project.getDescription(), "实施状态");
        String cleanDescription = buildDescriptionWithCurrentTags(
            project.getDescription(),
            resolvedProjectTier,
            hasFeasibilityReport,
            implementationStatus
        );

        return ProjectDetailResponse.builder()
                .id(project.getProjectId())
                .name(project.getName())
                .description(cleanDescription)
                .managerId(managerId)
                .type(project.getProjectType())
                .status(statusValue)
                .projectStatus(project.getProjectStatus() != null ? project.getProjectStatus().name() : null)
                .productStatus(project.getProductStatus() != null ? project.getProductStatus().name() : null)
                .researchStatus(project.getResearchStatus() != null ? project.getResearchStatus().name() : (researchProfile != null && researchProfile.getStatus() != null ? researchProfile.getStatus().name() : null))
                .feasibilityReportUrl(feasibilityReport.map(a -> buildProjectAssetDownloadUrl(projectId, a.getId())).orElse(project.getFeasibilityReportUrl()))
                .projectTier(resolvedProjectTier)
                .targetUsers(productIdeaDetail != null ? productIdeaDetail.getTargetUsers() : null)
                .coreFeatures(productIdeaDetail != null ? productIdeaDetail.getCoreFeatures() : null)
                .useCase(productIdeaDetail != null ? productIdeaDetail.getUseCase() : null)
                .problemStatement(productIdeaDetail != null ? productIdeaDetail.getProblemStatement() : null)
                .techStackDesc(productIdeaDetail != null ? productIdeaDetail.getTechStackDesc() : null)
                .ideaOwnerUserId(productIdeaDetail != null ? productIdeaDetail.getIdeaOwnerUserId() : null)
                .promotionIcUserId(productIdeaDetail != null ? productIdeaDetail.getPromotionIcUserId() : null)
                .meetingParticipantUserIds(productIdeaDetail != null ? productIdeaDetail.getMeetingParticipantUserIds() : null)
                .demoEngineeringOwnerUserId(productIdeaDetail != null ? productIdeaDetail.getDemoEngineeringOwnerUserId() : null)
                .demoFileOwnerUserId(productIdeaDetail != null ? productIdeaDetail.getDemoFileOwnerUserId() : null)
                .demoDescriptionOwnerUserId(productIdeaDetail != null ? productIdeaDetail.getDemoDescriptionOwnerUserId() : null)
                .demoFeasibilityOwnerUserId(productIdeaDetail != null ? productIdeaDetail.getDemoFeasibilityOwnerUserId() : null)
                .hostUserId(researchProfile != null ? researchProfile.getHostUserId() : null)
                .chiefEngineerUserId(researchProfile != null ? researchProfile.getChiefEngineerUserId() : null)
                .blueprintOwnerUserId(researchProfile != null ? researchProfile.getBlueprintOwnerUserId() : null)
                .architectureOwnerUserId(researchProfile != null ? researchProfile.getArchitectureOwnerUserId() : null)
                .taskBreakdownOwnerUserId(researchProfile != null ? researchProfile.getTaskBreakdownOwnerUserId() : null)
                .evaluationReportOwnerUserId(researchProfile != null ? researchProfile.getEvaluationReportOwnerUserId() : null)
                .flowType(resolveFlowType(project, productIdeaDetail, researchProfile))
                .budget(project.getBudget())
                .cost(costSummaryRepository.findByProject_ProjectIdIn(List.of(projectId)).stream()
                        .map(cs -> cs.getTotalLaborCost() != null ? cs.getTotalLaborCost() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .criticalTask(project.getTechStack())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .members(memberInfos)
                .milestones(milestoneInfos)
                .uploads(uploadInfos)
                .subtasks(subtaskInfos)
                .build();
    }

    private String resolveFlowType(SysProject project,
                                   ProductIdeaDetail productIdeaDetail,
                                   ResearchProjectProfile researchProfile) {
        if (project.getFlowType() != null) {
            return project.getFlowType().name();
        }
        if (project.getProductStatus() != null || productIdeaDetail != null) {
            return FlowType.PRODUCT.name();
        }
        if (project.getResearchStatus() != null || researchProfile != null) {
            return FlowType.RESEARCH.name();
        }
        return FlowType.PROJECT.name();
    }

    @Transactional
    public void updateCriticalTask(String projectId, String taskContent, String userId) {
        SysProject project = projectRepository.findById(projectId).orElseThrow();
        if (!rbacService.isOwner(projectId, userId)) throw new PermissionDeniedException("权限不足");
        project.setTechStack(taskContent);
        projectRepository.save(project);
    }

    @Transactional
    public void addMilestone(String projectId, String title, String dateStr, String userId) {
        SysProject project = projectRepository.findById(projectId).orElseThrow();
        if (!rbacService.isOwner(projectId, userId)) throw new PermissionDeniedException("权限不足");
        ProjectMilestone milestone = ProjectMilestone.builder()
                .project(project).title(title).status(MilestoneStatus.PENDING)
                .dueDate(Instant.parse(dateStr)).createdAt(Instant.now()).build();
        milestoneRepository.save(milestone);
        notifyProjectMembers(projectId, userId, "MILESTONE_UPDATE", "项目里程碑已更新", "项目新增里程碑：" + title);
    }

    @Transactional
    public void transitionProjectStatus(String projectId, ProjectStatus newStatus, String userId) {
        SysProject project = projectRepository.findById(projectId).orElseThrow();
        if (!rbacService.isOwner(projectId, userId)) throw new PermissionDeniedException("权限不足");
        if (project.getFlowType() != FlowType.PROJECT) throw new RuntimeException("类型错误");

        ProjectStatus currentStatus = project.getProjectStatus();
        if (currentStatus == null) {
            throw new RuntimeException("项目当前状态异常");
        }
        if (currentStatus == newStatus) {
            return;
        }

        if (currentStatus == ProjectStatus.INITIATED && newStatus != ProjectStatus.IMPLEMENTING) {
            throw new RuntimeException("发起阶段仅允许进入实施阶段");
        }
        if (currentStatus == ProjectStatus.INITIATED) {
            String managerUserId = project.getManager() == null ? null : project.getManager().getUserId();
            if (project.getFeasibilityReportUrl() == null || project.getFeasibilityReportUrl().isBlank()) {
                throw new RuntimeException("请先由数据工程师上传可行性报告");
            }
            if (!hasSelectedManager(projectId, managerUserId)) {
                throw new RuntimeException("还需指定项目经理才可进入实施阶段");
            }
            if (!hasResponsibilityRatiosAllocated(projectId)) {
                throw new RuntimeException("还需完成权责比分配后才可进入实施阶段");
            }
            if (!hasImplementationStatusUpdated(project)) {
                throw new RuntimeException("还需由数据工程师更新实施状态后才可进入实施阶段");
            }
            if (!hasRequiredExecutionMembers(projectId, managerUserId)) {
                throw new RuntimeException("项目成员不足，至少需要1名可执行成员后才能进入实施阶段");
            }
        }
        if (currentStatus == ProjectStatus.IMPLEMENTING && newStatus != ProjectStatus.SETTLEMENT) {
            throw new RuntimeException("实施阶段仅允许进入结算阶段");
        }
        if (currentStatus == ProjectStatus.IMPLEMENTING && newStatus == ProjectStatus.SETTLEMENT && !hasRequiredExecutionMembers(projectId, project.getManager() == null ? null : project.getManager().getUserId())) {
            throw new RuntimeException("项目成员不足，至少需要1名可执行成员后才能进入结算阶段");
        }
        if (newStatus == ProjectStatus.SETTLEMENT && projectSubtaskRepository.countByProjectIdAndCompletedFalse(projectId) > 0) {
            throw new RuntimeException("仍有未完成的子任务，需由 Manager 确认全部完成后才能进入结算");
        }
        if (currentStatus == ProjectStatus.SETTLEMENT && newStatus != ProjectStatus.COMPLETED) {
            throw new RuntimeException("结算阶段仅允许进入归档阶段");
        }
        if (currentStatus == ProjectStatus.COMPLETED) {
            throw new RuntimeException("归档阶段不允许继续推进");
        }

        project.setProjectStatus(newStatus);
        if (newStatus == ProjectStatus.COMPLETED) {
            project.setEndDate(Instant.now());
        }
        projectRepository.save(project);
        notifyProjectMembers(projectId, userId, "PROJECT_STATUS", "项目进度已更新", "项目状态已变更为：" + newStatus.getStageName());
    }

    @Transactional
    public void transitionProductStatus(String projectId, ProductStatus newStatus, String userId) {
        SysProject project = projectRepository.findById(projectId).orElseThrow();
        if (!rbacService.isOwner(projectId, userId)) throw new PermissionDeniedException("权限不足");
        if (project.getFlowType() != FlowType.PRODUCT) throw new RuntimeException("类型错误");

        ProductStatus currentStatus = project.getProductStatus() == null ? ProductStatus.IDEA : project.getProductStatus();
        if (currentStatus == ProductStatus.LAUNCHED || currentStatus == ProductStatus.SHELVED) {
            throw new RuntimeException("终态项目不允许继续手动推进");
        }

        if (!isValidProductStatusTransition(currentStatus, newStatus)) {
            throw new RuntimeException("产品状态仅允许按阶段顺序推进");
        }

        if (newStatus == ProductStatus.DEMO_EXECUTION && project.getProjectTier() == null) {
            throw new RuntimeException("进入 Demo 阶段前必须完成项目评级");
        }
        if (newStatus == ProductStatus.DEMO_EXECUTION && project.getProjectType() == null) {
            throw new RuntimeException("进入 Demo 阶段前必须完成行业分类");
        }

        long memberCount = projectMemberRepository.findByProjectId(projectId).stream()
                .filter(member -> member.getUser() != null && member.getUser().getUserId() != null)
                .map(member -> member.getUser().getUserId())
                .distinct()
                .count();
        if (memberCount < 2) {
            throw new RuntimeException("项目成员不足，至少需要2名成员后才能推进产品流状态");
        }

        project.setProductStatus(newStatus);
        projectRepository.save(project);
    }

    private boolean isValidProductStatusTransition(ProductStatus currentStatus, ProductStatus nextStatus) {
        return switch (currentStatus) {
            case IDEA -> nextStatus == ProductStatus.PROMOTION || nextStatus == ProductStatus.DEMO_EXECUTION;
            case PROMOTION -> nextStatus == ProductStatus.DEMO_EXECUTION;
            case DEMO_EXECUTION -> nextStatus == ProductStatus.MEETING_DECISION;
            case MEETING_DECISION -> nextStatus == ProductStatus.TESTING || nextStatus == ProductStatus.SHELVED;
            case TESTING -> nextStatus == ProductStatus.LAUNCHED || nextStatus == ProductStatus.SHELVED;
            default -> false;
        };
    }

    @Transactional
    public void deleteProject(String projectId, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new PermissionDeniedException("未登录");
        }
        if (!isAdminRole(currentUser.getRole(), currentUser.getUsername())) {
            throw new PermissionDeniedException("仅管理员可删除项目");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));

        deleteProjectFiles(projectId);
        PROJECT_SCOPED_TABLES.forEach(table -> jdbcTemplate.update("DELETE FROM " + table + " WHERE project_id = ?", projectId));
        jdbcTemplate.update("DELETE FROM sys_project WHERE project_id = ?", projectId);
    }

    public List<SysProject> getManagedProjects(UserPrincipal currentUser) {
        if (currentUser != null && isAdminRole(currentUser.getRole(), currentUser.getUsername())) {
            return getAllProjectsOrdered();
        }
        return projectRepository.findManagedProjects(currentUser.getId());
    }

    @Transactional(readOnly = true)
    public ManagedProjectsSummaryResponse getManagedProjectsSummary(UserPrincipal currentUser) {
        List<SysProject> managedProjects = getManagedProjects(currentUser).stream()
                .filter(this::isActiveProjectFlow)
                .toList();
        List<SysProject> participatedProjects = getParticipatedProjects(currentUser).stream()
                .filter(this::isActiveProjectFlow)
                .toList();
        List<String> projectIds = managedProjects.stream().map(SysProject::getProjectId).toList();

        Map<String, Long> pendingSubtasksByProject = new HashMap<>();
        if (!projectIds.isEmpty()) {
            projectSubtaskRepository.countPendingByProjectIds(projectIds).forEach(view ->
                    pendingSubtasksByProject.put(view.getProjectId(), view.getPendingCount() == null ? 0L : view.getPendingCount()));
        }

        Map<String, ProjectExecutionPlan> executionPlanByProject = projectIds.isEmpty()
                ? Map.of()
                : executionPlanRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(ProjectExecutionPlan::getProjectId, plan -> plan, (left, right) -> right));

        Map<String, ProjectFinancialMetricsService.ProjectFinancialSnapshot> projectFinancialSnapshots =
                projectFinancialMetricsService.getProjectSnapshots(managedProjects, executionPlanByProject);

        Map<String, List<ProjectMemberSchedule>> schedulesByProject = projectIds.isEmpty()
                ? Map.of()
                : projectMemberScheduleRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.groupingBy(ProjectMemberSchedule::getProjectId));

        BigDecimal totalBudget = managedProjects.stream()
                .map(project -> projectFinancialSnapshots.get(project.getProjectId()))
                .filter(Objects::nonNull)
                .map(ProjectFinancialMetricsService.ProjectFinancialSnapshot::estimatedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = managedProjects.stream()
                .map(project -> projectFinancialSnapshots.get(project.getProjectId()))
                .filter(Objects::nonNull)
                .map(ProjectFinancialMetricsService.ProjectFinancialSnapshot::humanCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRemainingProfit = managedProjects.stream()
                .map(project -> projectFinancialSnapshots.get(project.getProjectId()))
                .filter(Objects::nonNull)
                .map(ProjectFinancialMetricsService.ProjectFinancialSnapshot::remainingProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ManagedProjectsSummaryResponse.ProjectCard> cards = managedProjects.stream()
                .map(project -> {
                    String projectId = project.getProjectId();
                    long pendingSubtasks = pendingSubtasksByProject.getOrDefault(projectId, 0L);
                    ProjectFinancialMetricsService.ProjectFinancialSnapshot financialSnapshot = projectFinancialSnapshots.get(projectId);
                    List<String> riskSignals = new java.util.ArrayList<>();
                    ProjectExecutionPlan plan = executionPlanByProject.get(projectId);
                    if (project.getFlowType() == FlowType.PROJECT) {
                        if (plan == null || plan.getGoalDescription() == null || plan.getGoalDescription().isBlank()) {
                            riskSignals.add("缺少实施目标");
                        }
                        if (plan == null || plan.getProjectTier() == null) {
                            riskSignals.add("未评级");
                        }
                    }
                    int delayedMembers = (int) schedulesByProject.getOrDefault(projectId, List.of()).stream()
                            .filter(schedule -> schedule.getExpectedEndDate() != null)
                            .filter(schedule -> {
                                Instant baseline = schedule.getActualEndDate() != null ? schedule.getActualEndDate() : Instant.now();
                                return baseline.isAfter(schedule.getExpectedEndDate());
                            })
                            .count();
                    if (delayedMembers > 0) {
                        riskSignals.add("存在延期成员");
                    }
                    if (pendingSubtasks > 0) {
                        riskSignals.add("存在未完成子任务");
                    }
                    return ManagedProjectsSummaryResponse.ProjectCard.builder()
                            .projectId(project.getProjectId())
                            .name(project.getName())
                            .description(project.getDescription())
                            .projectType(inferProjectType(project).name())
                            .flowType(resolveFlowTypeForSummary(project))
                            .status(resolveSummaryStatus(project))
                            .budget(financialSnapshot == null ? BigDecimal.ZERO : financialSnapshot.estimatedRevenue())
                            .cost(financialSnapshot == null ? BigDecimal.ZERO : financialSnapshot.humanCost())
                            .remainingProfit(financialSnapshot == null ? BigDecimal.ZERO : financialSnapshot.remainingProfit())
                            .pendingSubtaskCount((int) pendingSubtasks)
                            .delayedMemberCount(delayedMembers)
                            .risk(!riskSignals.isEmpty())
                            .riskSignals(riskSignals)
                            .managerName(project.getManager() == null ? null : project.getManager().getName())
                            .managerAvatar(project.getManager() == null || Boolean.TRUE.equals(project.getManager().getHiddenAvatar()) ? null : project.getManager().getAvatar())
                            .createdAt(project.getCreatedAt())
                            .projectTier(plan != null && plan.getProjectTier() != null ? plan.getProjectTier().name() : (project.getProjectTier() == null ? null : project.getProjectTier().name()))
                            .build();
                })
                .collect(Collectors.toList());

        BigDecimal profitMargin = totalBudget.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : totalRemainingProfit.multiply(BigDecimal.valueOf(100)).divide(totalBudget, 1, java.math.RoundingMode.HALF_UP);
        BigDecimal costUsageRate = totalBudget.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : totalCost.multiply(BigDecimal.valueOf(100)).divide(totalBudget, 1, java.math.RoundingMode.HALF_UP);
        BigDecimal managementRadius = participatedProjects.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(managedProjects.size())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(participatedProjects.size()), 1, java.math.RoundingMode.HALF_UP);

        return ManagedProjectsSummaryResponse.builder()
                .activeProjectCount((int) managedProjects.stream().filter(project -> project.getProjectStatus() != ProjectStatus.COMPLETED).count())
                .pendingSubtaskCount(cards.stream().mapToInt(ManagedProjectsSummaryResponse.ProjectCard::getPendingSubtaskCount).sum())
                .riskProjectCount((int) cards.stream().filter(ManagedProjectsSummaryResponse.ProjectCard::isRisk).count())
                .managementRadius(managementRadius)
                .totalBudget(totalBudget)
                .totalCost(totalCost)
                .totalHumanCost(totalCost)
                .totalRemainingProfit(totalRemainingProfit)
                .profitMargin(profitMargin)
                .costUsageRate(costUsageRate)
                .lastCostBatchAt(projectFinancialMetricsService.getLatestCompletedCostBatchAt())
                .projects(cards)
                .build();
    }

    private boolean isActiveProjectFlow(SysProject project) {
        if (project == null) {
            return false;
        }
        FlowType flowType = project.getFlowType();
        if (flowType == FlowType.PRODUCT) {
            return project.getProductStatus() != ProductStatus.SHELVED;
        }
        if (flowType == FlowType.RESEARCH) {
            return project.getResearchStatus() != ResearchStatus.ARCHIVE
                    && project.getResearchStatus() != ResearchStatus.SHELVED;
        }
        return project.getProjectStatus() != ProjectStatus.COMPLETED;
    }

    private String resolveFlowTypeForSummary(SysProject project) {
        if (project == null || project.getFlowType() == null) {
            return FlowType.PROJECT.name();
        }
        return project.getFlowType().name();
    }

    private String resolveSummaryStatus(SysProject project) {
        if (project == null) {
            return null;
        }
        if (project.getFlowType() == FlowType.PRODUCT) {
            return project.getProductStatus() == null ? null : project.getProductStatus().name();
        }
        if (project.getFlowType() == FlowType.RESEARCH) {
            return project.getResearchStatus() == null ? null : project.getResearchStatus().name();
        }
        return project.getProjectStatus() == null ? null : project.getProjectStatus().getStageName();
    }

    public List<SysProject> getParticipatedProjects(UserPrincipal currentUser) {
        if (currentUser != null && isAdminRole(currentUser.getRole(), currentUser.getUsername())) {
            return getAllProjectsOrdered();
        }
        return projectRepository.findParticipatedProjects(currentUser.getId());
    }

    @Transactional
    public Map<String, Object> updateProjectDynamicInfo(String projectId,
                                                        ProjectDynamicInfoUpdateRequest request,
                                                        String userId,
                                                        String userRole,
                                                        String username) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));

        if (project.getFlowType() != FlowType.PROJECT) {
            throw new BusinessException("仅项目交付流支持动态信息维护");
        }
        if (project.getProjectStatus() == ProjectStatus.COMPLETED) {
            throw new BusinessException("归档阶段不允许继续编辑动态信息");
        }
        if (!canEditProjectDynamicInfo(projectId, userId, userRole, username)) {
            throw new PermissionDeniedException("仅被选中的数据工程师或管理员可编辑动态信息");
        }

        String goalDescription = request == null ? "" : String.valueOf(request.getGoalDescription() == null ? "" : request.getGoalDescription()).trim();
        String techStackDescription = request == null ? "" : String.valueOf(request.getTechStackDescription() == null ? "" : request.getTechStackDescription()).trim();
        String implementationStatus = request == null ? "" : String.valueOf(request.getImplementationStatus() == null ? "" : request.getImplementationStatus()).trim();

        if (goalDescription.isBlank()) {
            throw new BusinessException("请填写关键目标");
        }
        if (techStackDescription.isBlank()) {
            throw new BusinessException("请填写技术栈与深度");
        }
        if (request == null || request.getProjectTier() == null) {
            throw new BusinessException("请选择项目评级");
        }
        if (implementationStatus.isBlank()) {
            throw new BusinessException("请填写实施状态");
        }

        ProjectExecutionPlan plan = executionPlanRepository.findByProjectId(projectId)
                .orElseGet(() -> {
                    ProjectExecutionPlan created = new ProjectExecutionPlan();
                    created.setProjectId(projectId);
                    created.setCreatedBy(userId);
                    return created;
                });
        if (plan.getDifficultyLevel() == null || plan.getDifficultyLevel().isBlank()) {
            plan.setDifficultyLevel(request.getProjectTier().name());
        }
        if (plan.getCreatedBy() == null || plan.getCreatedBy().isBlank()) {
            plan.setCreatedBy(userId);
        }
        plan.setGoalDescription(goalDescription);
        plan.setProjectTier(request.getProjectTier());
        plan.setTechStackDescription(techStackDescription);
        executionPlanRepository.save(plan);

        if (request.getEstimatedRevenue() != null) {
            project.setEstimatedRevenue(request.getEstimatedRevenue());
            project.setBudget(request.getEstimatedRevenue());
        }
        project.setProjectTier(request.getProjectTier());
        String description = upsertProjectTier(project.getDescription(), request.getProjectTier());
        description = upsertFeasibilityReportStatus(description, hasFeasibilityReport(project) ? "已上传" : "未上传");
        description = upsertImplementationStatus(description, implementationStatus);
        project.setDescription(description);
        projectRepository.save(project);

        notifyProjectMembers(projectId, userId, "PROJECT_DYNAMIC_INFO", "项目动态信息已更新", "项目关键目标、评级、技术栈或实施状态已更新");

        return Map.of(
                "success", true,
                "message", "动态信息已更新",
                "projectId", projectId,
                "projectTier", request.getProjectTier().name(),
                "goalDescription", goalDescription,
                "techStackDescription", techStackDescription,
                "implementationStatus", implementationStatus
        );
    }

    private void checkProjectAccess(SysProject project, UserPrincipal currentUser) {
        if (isAdminRole(currentUser.getRole(), currentUser.getUsername())) return;
        if (!project.getManager().getUserId().equals(currentUser.getId())) {
            throw new PermissionDeniedException("仅限当前项目管理员可操作");
        }
    }

    private boolean canEditProjectDynamicInfo(String projectId, String userId, String userRole, String username) {
        if (isAdminRole(userRole, username)) {
            return true;
        }
        return projectMemberRepository.findByProjectIdAndUserUserId(projectId, userId)
                .filter(member -> {
                    String role = member.getRole() == null ? "" : member.getRole().trim().toUpperCase();
                    return Set.of("DATA", "DATA_ENGINEER", "MANAGER", "PM").contains(role);
                })
                .isPresent();
    }

    private boolean isAdminRole(String role, String username) {
        if (role != null && "ADMIN".equalsIgnoreCase(role.trim())) {
            return true;
        }
        return username != null && getAdminUsernames().contains(username.trim());
    }

    private boolean isAdminRole(String role) {
        return role != null && "ADMIN".equalsIgnoreCase(role.trim());
    }

    private List<SysProject> getAllProjectsOrdered() {
        return projectRepository.findAll().stream()
                .sorted(Comparator.comparing(SysProject::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SysProject::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private void deleteProjectFiles(String projectId) {
        deleteDirectoryIfExists(Paths.get(uploadDir).resolve(projectId));
        deleteDirectoryIfExists(Paths.get(System.getProperty("user.dir"), "uploads", "execution", projectId));
        deleteDirectoryIfExists(Paths.get(System.getProperty("user.dir"), "uploads", "settlement", projectId));
    }

    private void deleteDirectoryIfExists(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()).forEach(current -> {
                    try {
                        Files.deleteIfExists(current);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw new BusinessException("删除项目文件失败: " + ioException.getMessage());
            }
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("删除项目文件失败: " + ex.getMessage());
        }
    }

    @Transactional
    public void uploadProjectAsset(String projectId, MultipartFile file, String userId, String assetCategory) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));

        String normalizedAssetCategory = normalizeProjectAssetCategory(assetCategory);
        boolean isFeasibilityReport = FEASIBILITY_REPORT_ASSET_CATEGORY.equals(normalizedAssetCategory);
        validateProjectAssetUpload(project, userId, normalizedAssetCategory, isFeasibilityReport);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) originalFilename = "unknown_file";

        String fileType = "FILE";
        if (originalFilename.contains(".")) {
            fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toUpperCase();
        }

        try {
            Path projectUploadDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(projectId);
            Files.createDirectories(projectUploadDir);

            Path targetPath = projectUploadDir.resolve(buildStoredFileName(originalFilename));
            file.transferTo(targetPath);

            User uploader = userRepository.findById(userId).orElse(null);
            ProjectAsset asset = ProjectAsset.builder()
                    .project(project)
                    .fileName(originalFilename)
                    .fileType(fileType)
                    .filePath(targetPath.toString())
                    .fileData(file.getBytes())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .uploaderName(uploader != null ? uploader.getName() : "Unknown")
                    .uploadedAt(Instant.now())
                    .assetCategory(normalizedAssetCategory)
                    .build();

            assetRepository.save(asset);
            if (isFeasibilityReport) {
                project.setFeasibilityReportUrl(buildProjectAssetDownloadUrl(projectId, asset.getId()));
                project.setDescription(upsertFeasibilityReportStatus(project.getDescription(), "已上传"));
                projectRepository.save(project);
                tryAutoAdvanceStatus(project);
            }

        } catch (IOException e) {
            throw new BusinessException("文件存储失败: " + e.getMessage());
        }
    }

    private String buildStoredFileName(String originalFilename) {
        int extensionIndex = originalFilename.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == originalFilename.length() - 1) {
            return UUID.randomUUID() + "_" + originalFilename;
        }

        String name = originalFilename.substring(0, extensionIndex);
        String extension = originalFilename.substring(extensionIndex);
        return UUID.randomUUID() + "_" + name + extension;
    }

    @Transactional(readOnly = true)
    public ProjectAsset getProjectAsset(String projectId, Long assetId, String userId) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在"));

        boolean hasAccess = rbacService.isOwner(projectId, userId)
                || projectMemberRepository.findByProjectIdAndUserUserId(projectId, userId).isPresent();

        if (!hasAccess) {
            throw new PermissionDeniedException("仅限当前项目管理员可以查看可行性报告");
        }

        return assetRepository.findByIdAndProjectProjectId(assetId, projectId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
    }

    private String buildProjectAssetDownloadUrl(String projectId, Long assetId) {
        return assetId == null ? null : "/api/projects/" + projectId + "/assets/" + assetId + "/download";
    }

    private void tryAutoAdvanceStatus(SysProject project) {
        boolean statusChanged = false;
        if (project.getFlowType() == FlowType.PRODUCT) {
            return;
        } else {
            switch (project.getProjectStatus()) {
                case INITIATED:
                    // 新规则：可行性报告已上传且已指定项目经理（并有执行成员）时进入实施阶段
                    String managerUserId = project.getManager() == null ? null : project.getManager().getUserId();
                    if (project.getFeasibilityReportUrl() != null && !project.getFeasibilityReportUrl().isBlank()
                            && hasSelectedManager(project.getProjectId(), managerUserId)
                            && hasResponsibilityRatiosAllocated(project.getProjectId())
                            && hasImplementationStatusUpdated(project)
                            && hasRequiredExecutionMembers(project.getProjectId(), managerUserId)) {
                        project.setProjectStatus(ProjectStatus.IMPLEMENTING);
                        statusChanged = true;
                    }
                    break;
                default: break;
            }
        }
        if (statusChanged) {
            projectRepository.save(project);
            notifyManagerExecutionPlanning(project);
        }
    }

    private boolean hasSelectedManager(String projectId, String managerUserId) {
        if (managerUserId == null || managerUserId.isBlank()) {
            return false;
        }
        return projectMemberRepository.findByProjectIdAndUserUserId(projectId, managerUserId)
                .map(member -> {
                    String role = member.getRole() == null ? "" : member.getRole().trim().toUpperCase();
                    return Set.of("MANAGER", "BD", "BUSINESS", "DATA", "DATA_ENGINEER").contains(role);
                })
                .orElse(false);
    }

    private String normalizeProjectAssetCategory(String assetCategory) {
        String normalized = assetCategory == null ? "" : assetCategory.trim().toUpperCase();
        if (normalized.isBlank()) {
            return FEASIBILITY_REPORT_ASSET_CATEGORY;
        }
        if (!Set.of(
                FEASIBILITY_REPORT_ASSET_CATEGORY,
                INITIATION_ATTACHMENT_ASSET_CATEGORY,
                RESEARCH_BLUEPRINT_DOC,
                RESEARCH_ARCHITECTURE_DOC,
                RESEARCH_TASK_BREAKDOWN_DOC,
                RESEARCH_EVALUATION_REPORT
        ).contains(normalized)) {
            throw new BusinessException("不支持的项目文件分类");
        }
        return normalized;
    }

    private void validateProjectAssetUpload(SysProject project, String userId, String assetCategory, boolean isFeasibilityReport) {
        if (project.getFlowType() == FlowType.RESEARCH) {
            validateResearchAssetUpload(project, userId, assetCategory);
            return;
        }
        if (project.getFlowType() != FlowType.PROJECT) {
            throw new BusinessException("当前流转类型不支持该资料上传方式");
        }

        if (project.getProjectStatus() != ProjectStatus.INITIATED) {
            throw new BusinessException(isFeasibilityReport ? "仅发起阶段允许上传可行性报告" : "仅发起阶段允许上传项目发起附件");
        }

        if (isFeasibilityReport) {
            String selectedDataEngineerId = projectMemberRepository.findByProjectId(project.getProjectId()).stream()
                    .filter(member -> member.getRole() != null && "DATA_ENGINEER".equalsIgnoreCase(member.getRole().trim()))
                    .map(member -> member.getUser() == null ? null : member.getUser().getUserId())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            if (selectedDataEngineerId == null) {
                throw new BusinessException("项目未指定数据工程师，无法上传可行性报告");
            }

            if (!selectedDataEngineerId.equals(userId)) {
                throw new PermissionDeniedException("仅被选中的数据工程师可上传可行性报告");
            }
        } else {
            projectMemberRepository.findByProjectIdAndUserUserId(project.getProjectId(), userId)
                    .orElseThrow(() -> new PermissionDeniedException("仅项目成员可上传发起附件"));
        }
    }

    private void validateResearchAssetUpload(SysProject project, String userId, String assetCategory) {
        ResearchProjectProfile profile = researchProjectProfileRepository.findByProjectId(project.getProjectId())
                .orElseThrow(() -> new BusinessException("科研流缺少档案信息"));
        String expectedOwnerId = switch (assetCategory) {
            case RESEARCH_BLUEPRINT_DOC -> profile.getBlueprintOwnerUserId();
            case RESEARCH_ARCHITECTURE_DOC -> profile.getArchitectureOwnerUserId();
            case RESEARCH_TASK_BREAKDOWN_DOC -> profile.getTaskBreakdownOwnerUserId();
            case RESEARCH_EVALUATION_REPORT -> profile.getEvaluationReportOwnerUserId();
            default -> null;
        };
        if (expectedOwnerId == null || expectedOwnerId.isBlank()) {
            throw new BusinessException("科研关键文件责任人尚未配置");
        }
        if (!expectedOwnerId.equals(userId)) {
            throw new PermissionDeniedException("仅关键文件责任人可上传该科研资料");
        }
    }

    private void notifyManagerExecutionPlanning(SysProject project) {
        if (project == null || project.getProjectStatus() != ProjectStatus.IMPLEMENTING) {
            return;
        }
        String managerUserId = project.getManager() == null ? null : project.getManager().getUserId();
        if (managerUserId == null || managerUserId.isBlank()) {
            return;
        }
        internalMessageService.sendMessage(
                managerUserId,
                "EXECUTION_PLANNING",
                "项目进入实施阶段，请规划任务",
                "请尽快规划开发与算法任务，并选择对应工程师进行分配执行。",
                project.getProjectId()
        );
    }

    private boolean hasRequiredExecutionMembers(String projectId, String managerUserId) {
        List<SysProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        if (members == null || members.isEmpty()) {
            return false;
        }
        return members.stream().anyMatch(member -> {
            if (member.getUser() == null || member.getUser().getUserId() == null) {
                return false;
            }
            String uid = member.getUser().getUserId();
            String role = normalizeProjectMemberRole(member.getRole());
            int executionWeight = sanitizeRatio(member.getWeight());
            if (!isExecutionMemberRole(role) || executionWeight <= 0) {
                return false;
            }
            if (managerUserId != null && managerUserId.equals(uid)) {
                return Set.of("DATA", "DATA_ENGINEER").contains(role);
            }
            return true;
        });
    }

    private boolean hasResponsibilityRatiosAllocated(String projectId) {
        List<SysProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        if (members == null || members.isEmpty()) {
            return false;
        }
        int total = members.stream()
                .mapToInt(member -> sanitizeRatio(member.getWeight()) + sanitizeRatio(member.getManagerWeight()))
                .sum();
        return total == 100;
    }

    private boolean hasImplementationStatusUpdated(SysProject project) {
        String status = extractImplementationStatus(project.getDescription());
        return status != null && !status.isBlank() && !"未设置".equals(status.trim());
    }

    public Map<String, Object> updateImplementationStatus(String projectId, String statusText, String userId) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));
        if (project.getFlowType() != FlowType.PROJECT) {
            throw new BusinessException("仅项目流支持实施状态更新");
        }

        boolean isDataEngineer = projectMemberRepository.findByProjectIdAndUserUserId(projectId, userId)
                .map(member -> "DATA_ENGINEER".equalsIgnoreCase(String.valueOf(member.getRole())))
                .orElse(false);
        if (!isDataEngineer) {
            throw new PermissionDeniedException("仅被选中的数据工程师可更新实施状态");
        }

        String normalized = statusText == null ? "" : statusText.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("实施状态不能为空");
        }

        String description = upsertImplementationStatus(project.getDescription(), normalized);
        project.setDescription(description);
        projectRepository.save(project);

        tryAutoAdvanceStatus(project);
        return Map.of("success", true, "implementationStatus", normalized, "projectId", projectId);
    }

    private String extractImplementationStatus(String description) {
        String text = description == null ? "" : description;
        int start = text.indexOf(IMPLEMENTATION_STATUS_TAG);
        if (start < 0) {
            return null;
        }
        int valueStart = start + IMPLEMENTATION_STATUS_TAG.length();
        int end = text.indexOf("|", valueStart);
        if (end < 0) {
            end = text.length();
        }
        return text.substring(valueStart, end).trim();
    }

    private String upsertImplementationStatus(String description, String statusText) {
        String text = description == null ? "" : description;
        String replacement = IMPLEMENTATION_STATUS_TAG + statusText;
        if (text.contains(IMPLEMENTATION_STATUS_TAG)) {
            return IMPLEMENTATION_STATUS_PATTERN.matcher(text).replaceFirst(replacement).trim();
        }
        if (text.isBlank()) {
            return replacement;
        }
        return (text + " | " + replacement).trim();
    }

    private String upsertProjectTier(String description, ProjectTierEnum projectTier) {
        String value = projectTier == null ? "未定级" : projectTier.name();
        String text = description == null ? "" : description;
        String replacement = PROJECT_TIER_TAG + value;
        if (text.contains(PROJECT_TIER_TAG)) {
            return PROJECT_TIER_PATTERN.matcher(text).replaceFirst(replacement).trim();
        }
        if (text.isBlank()) {
            return replacement;
        }
        return (text + " | " + replacement).trim();
    }

    private String upsertFeasibilityReportStatus(String description, String status) {
        String text = description == null ? "" : description;
        String replacement = FEASIBILITY_REPORT_STATUS_TAG + status;
        if (text.contains(FEASIBILITY_REPORT_STATUS_TAG) || text.contains("【可行性报告URL】:")) {
            return FEASIBILITY_REPORT_STATUS_PATTERN.matcher(text).replaceFirst(replacement).trim();
        }
        if (text.isBlank()) {
            return replacement;
        }
        return (text + " | " + replacement).trim();
    }

    private boolean hasFeasibilityReport(SysProject project) {
        return isPresent(project.getFeasibilityReportUrl())
                || assetRepository.findByProjectProjectIdOrderByUploadedAtDesc(project.getProjectId()).stream()
                .anyMatch(asset -> FEASIBILITY_REPORT_ASSET_CATEGORY.equalsIgnoreCase(asset.getAssetCategory()));
    }

    private boolean isPresent(String value) {
        return value != null && !value.trim().isBlank();
    }

    @Transactional
    public ProjectSubtaskResponse createSubtask(String projectId, ProjectSubtaskRequest request, String userId) {
        assertManager(projectId, userId);
        User assignee = request.getAssigneeUserId() == null || request.getAssigneeUserId().isBlank()
                ? null
                : userRepository.findById(request.getAssigneeUserId()).orElseThrow(() -> new BusinessException("负责人不存在"));
        ProjectSubtask subtask = ProjectSubtask.builder()
                .projectId(projectId)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .assigneeUserId(assignee == null ? null : assignee.getUserId())
                .assigneeName(assignee == null ? null : (assignee.getName() == null ? assignee.getUsername() : assignee.getName()))
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .createdBy(userId)
                .build();
        projectSubtaskRepository.save(subtask);
        if (assignee != null) {
            internalMessageService.sendMessage(assignee.getUserId(), "SUBTASK_ASSIGNED", "你被分配了新的子任务", subtask.getTitle(), projectId);
        }
        return toSubtaskResponse(subtask);
    }

    @Transactional
    public ProjectSubtaskResponse updateSubtask(String projectId, Long subtaskId, ProjectSubtaskRequest request, String userId) {
        assertManager(projectId, userId);
        ProjectSubtask subtask = projectSubtaskRepository.findById(subtaskId)
                .filter(task -> projectId.equals(task.getProjectId()))
                .orElseThrow(() -> new BusinessException("子任务不存在"));
        User assignee = request.getAssigneeUserId() == null || request.getAssigneeUserId().isBlank()
                ? null
                : userRepository.findById(request.getAssigneeUserId()).orElseThrow(() -> new BusinessException("负责人不存在"));
        subtask.setTitle(request.getTitle().trim());
        subtask.setDescription(request.getDescription());
        subtask.setAssigneeUserId(assignee == null ? null : assignee.getUserId());
        subtask.setAssigneeName(assignee == null ? null : (assignee.getName() == null ? assignee.getUsername() : assignee.getName()));
        subtask.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        projectSubtaskRepository.save(subtask);
        return toSubtaskResponse(subtask);
    }

    @Transactional
    public void completeSubtask(String projectId, Long subtaskId, String userId) {
        assertManager(projectId, userId);
        ProjectSubtask subtask = projectSubtaskRepository.findById(subtaskId)
                .filter(task -> projectId.equals(task.getProjectId()))
                .orElseThrow(() -> new BusinessException("子任务不存在"));
        subtask.setCompleted(true);
        subtask.setCompletedAt(Instant.now());
        projectSubtaskRepository.save(subtask);
    }

    public FinanceDashboardResponse getFinanceDashboard() {
        List<SysProject> all = projectRepository.findAll();
        List<String> projectIds = all.stream()
                .map(SysProject::getProjectId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        Map<String, BigDecimal> costByProject = all.stream()
                .collect(Collectors.toMap(
                        SysProject::getProjectId,
                        p -> p.getCost() != null ? p.getCost() : BigDecimal.ZERO,
                        BigDecimal::add));
        Map<String, List<SysProjectMember>> memberMap = projectIds.isEmpty()
                ? Map.of()
                : projectMemberRepository.findByProjectIdInWithUser(projectIds).stream()
                .collect(Collectors.groupingBy(SysProjectMember::getProjectId));
        Map<String, ProductIdeaDetail> productIdeaMap = projectIds.isEmpty()
                ? Map.of()
                : productIdeaDetailRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(ProductIdeaDetail::getProjectId, item -> item, (left, right) -> right));
        Map<String, ResearchProjectProfile> researchProfileMap = projectIds.isEmpty()
                ? Map.of()
                : researchProjectProfileRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(ResearchProjectProfile::getProjectId, item -> item, (left, right) -> right));

        Set<String> referencedOwnerIds = new java.util.HashSet<>();
        all.forEach(project -> {
            if (project.getManager() != null && project.getManager().getUserId() != null) {
                referencedOwnerIds.add(project.getManager().getUserId());
            }
            ProductIdeaDetail ideaDetail = productIdeaMap.get(project.getProjectId());
            if (ideaDetail != null && ideaDetail.getIdeaOwnerUserId() != null && !ideaDetail.getIdeaOwnerUserId().isBlank()) {
                referencedOwnerIds.add(ideaDetail.getIdeaOwnerUserId());
            }
            ResearchProjectProfile profile = researchProfileMap.get(project.getProjectId());
            if (profile != null) {
                if (profile.getChiefEngineerUserId() != null && !profile.getChiefEngineerUserId().isBlank()) {
                    referencedOwnerIds.add(profile.getChiefEngineerUserId());
                }
                if (profile.getIdeaOwnerUserId() != null && !profile.getIdeaOwnerUserId().isBlank()) {
                    referencedOwnerIds.add(profile.getIdeaOwnerUserId());
                }
            }
        });
        Map<String, User> ownerUserMap = referencedOwnerIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(referencedOwnerIds).stream()
                .collect(Collectors.toMap(User::getUserId, user -> user, (left, right) -> right));

        List<FinanceDashboardResponse.FlowProjects> projectFlow = all.stream()
                .filter(p -> p.getFlowType() == FlowType.PROJECT)
                .filter(p -> p.getProjectStatus() != ProjectStatus.COMPLETED)
                .map(p -> toFlowProject(
                        p,
                        memberMap.getOrDefault(p.getProjectId(), List.of()),
                        productIdeaMap.get(p.getProjectId()),
                        researchProfileMap.get(p.getProjectId()),
                        ownerUserMap,
                        costByProject
                ))
                .toList();

        List<FinanceDashboardResponse.FlowProjects> productFlow = all.stream()
                .filter(p -> p.getFlowType() == FlowType.PRODUCT)
                .filter(p -> p.getProductStatus() != ProductStatus.SHELVED)
                .map(p -> toFlowProject(
                        p,
                        memberMap.getOrDefault(p.getProjectId(), List.of()),
                        productIdeaMap.get(p.getProjectId()),
                        researchProfileMap.get(p.getProjectId()),
                        ownerUserMap,
                        costByProject
                ))
                .toList();

        List<FinanceDashboardResponse.FlowProjects> researchFlow = all.stream()
                .filter(p -> p.getFlowType() == FlowType.RESEARCH)
                .filter(p -> p.getResearchStatus() != ResearchStatus.ARCHIVE)
                .map(p -> toFlowProject(
                        p,
                        memberMap.getOrDefault(p.getProjectId(), List.of()),
                        productIdeaMap.get(p.getProjectId()),
                        researchProfileMap.get(p.getProjectId()),
                        ownerUserMap,
                        costByProject
                ))
                .toList();

        return FinanceDashboardResponse.builder()
                .projects(projectFlow)
                .products(productFlow)
                .research(researchFlow)
                .build();
    }

    private FinanceDashboardResponse.FlowProjects toFlowProject(SysProject p,
                                                                List<SysProjectMember> memberList,
                                                                ProductIdeaDetail productIdeaDetail,
                                                                ResearchProjectProfile researchProfile,
                                                                Map<String, User> ownerUserMap,
                                                                Map<String, BigDecimal> costByProject) {
        List<FinanceDashboardResponse.MemberInfo> members = memberList.stream()
                .map(m -> FinanceDashboardResponse.MemberInfo.builder()
                        .userId(m.getUser().getUserId())
                        .name(m.getUser().getName() != null ? m.getUser().getName() : m.getUser().getUsername())
                        .role(m.getRole())
                        .build())
                .toList();
        OwnerRef owner = resolveFinanceOwner(p, members, productIdeaDetail, researchProfile, ownerUserMap);
        BigDecimal projectCost = costByProject.getOrDefault(p.getProjectId(), BigDecimal.ZERO);
        return FinanceDashboardResponse.FlowProjects.builder()
                .projectId(p.getProjectId())
                .name(p.getName())
                .projectType(inferProjectType(p).name())
                .projectTier(p.getProjectTier() != null ? p.getProjectTier().name() : null)
                .status(p.getCurrentStatus())
                .managerName(owner.name())
                .managerId(owner.userId())
                .primaryOwnerName(owner.name())
                .primaryOwnerId(owner.userId())
                .budget(p.getBudget())
                .cost(projectCost)
                .description(p.getDescription())
                .members(members)
                .build();
    }

    private OwnerRef resolveFinanceOwner(SysProject project,
                                         List<FinanceDashboardResponse.MemberInfo> members,
                                         ProductIdeaDetail productIdeaDetail,
                                         ResearchProjectProfile researchProfile,
                                         Map<String, User> ownerUserMap) {
        Map<String, String> memberNameMap = members.stream()
                .filter(member -> member.getUserId() != null && !member.getUserId().isBlank())
                .collect(Collectors.toMap(
                        FinanceDashboardResponse.MemberInfo::getUserId,
                        member -> member.getName() != null && !member.getName().isBlank() ? member.getName() : member.getUserId(),
                        (left, right) -> left
                ));

        String managerId = project.getManager() != null ? project.getManager().getUserId() : null;
        String managerName = formatUserName(project.getManager());

        String ownerId = null;
        if (project.getFlowType() == FlowType.PROJECT) {
            ownerId = (managerId != null && !managerId.isBlank()) ? managerId : findInitiatorUserId(members);
        } else if (project.getFlowType() == FlowType.PRODUCT) {
            ownerId = productIdeaDetail != null ? blankToNull(productIdeaDetail.getIdeaOwnerUserId()) : null;
            if (ownerId == null) {
                ownerId = (managerId != null && !managerId.isBlank()) ? managerId : findInitiatorUserId(members);
            }
        } else if (project.getFlowType() == FlowType.RESEARCH) {
            if (researchProfile != null) {
                ownerId = blankToNull(researchProfile.getChiefEngineerUserId());
                if (ownerId == null) {
                    ownerId = blankToNull(researchProfile.getIdeaOwnerUserId());
                }
            }
            if (ownerId == null) {
                ownerId = (managerId != null && !managerId.isBlank()) ? managerId : findInitiatorUserId(members);
            }
        }

        if (ownerId == null) {
            ownerId = managerId;
        }

        String ownerName = ownerId == null ? null : memberNameMap.get(ownerId);
        if ((ownerName == null || ownerName.isBlank()) && ownerId != null) {
            User fallback = ownerUserMap.get(ownerId);
            ownerName = formatUserName(fallback);
        }
        if ((ownerName == null || ownerName.isBlank()) && ownerId != null && ownerId.equals(managerId)) {
            ownerName = managerName;
        }
        if (ownerName == null || ownerName.isBlank()) {
            ownerName = managerName;
        }
        if ((ownerName == null || ownerName.isBlank()) && !members.isEmpty()) {
            ownerId = members.get(0).getUserId();
            ownerName = members.get(0).getName();
        }
        if (ownerName == null || ownerName.isBlank()) {
            ownerName = "未指定负责人";
        }

        return new OwnerRef(ownerId, ownerName);
    }

    private String findInitiatorUserId(List<FinanceDashboardResponse.MemberInfo> members) {
        return members.stream()
                .filter(member -> {
                    String role = String.valueOf(member.getRole() == null ? "" : member.getRole()).toUpperCase();
                    return role.equals("BUSINESS") || role.equals("BD") || role.equals("ADMIN") || role.equals("OWNER") || role.equals("INITIATOR");
                })
                .map(FinanceDashboardResponse.MemberInfo::getUserId)
                .filter(userId -> userId != null && !userId.isBlank())
                .findFirst()
                .orElseGet(() -> members.stream()
                        .map(FinanceDashboardResponse.MemberInfo::getUserId)
                        .filter(userId -> userId != null && !userId.isBlank())
                        .findFirst()
                        .orElse(null));
    }

    private String formatUserName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getUsername();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private record OwnerRef(String userId, String name) {
    }

    private ProjectType inferProjectType(SysProject project) {
        if (project.getProjectType() != null) {
            return project.getProjectType();
        }
        if (project.getFlowType() == FlowType.RESEARCH) {
            return ProjectType.AI_FOR_SCIENCE;
        }
        return ProjectType.BUSINESS;
    }

    private void notifyProjectMembers(String projectId, String operatorUserId, String type, String title, String content) {
        projectMemberRepository.findByProjectId(projectId).forEach(member -> {
            if (!member.getUser().getUserId().equals(operatorUserId)) {
                internalMessageService.sendMessage(member.getUser().getUserId(), type, title, content, projectId);
            }
        });
    }

    private void assertManager(String projectId, String userId) {
        SysProject project = projectRepository.findById(projectId).orElseThrow(() -> new BusinessException("项目不存在"));
        if (project.getManager() == null || !userId.equals(project.getManager().getUserId())) {
            throw new PermissionDeniedException("仅 Manager 可执行此操作");
        }
    }

    @Transactional
    public void adjustProjectCost(String projectId, AdjustProjectCostRequest request, MultipartFile invoiceFile) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        if (!isAdmin(currentUser)) {
            throw new PermissionDeniedException("仅管理员可调整项目成本");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        ProjectCostAdjustmentType type;
        try {
            type = ProjectCostAdjustmentType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的成本类型: " + request.getType());
        }

        if (request.getItemName() == null || request.getItemName().isBlank()) {
            throw new BusinessException("名称不能为空");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("金额必须大于0");
        }

        BigDecimal previousCost = project.getCost() != null ? project.getCost() : BigDecimal.ZERO;
        BigDecimal newCost = previousCost.add(request.getAmount());
        project.setCost(newCost);
        projectRepository.save(project);

        ProjectCostAdjustment adjustment = ProjectCostAdjustment.builder()
                .projectId(projectId)
                .projectName(project.getName())
                .adjustmentType(type)
                .itemName(request.getItemName().trim())
                .amount(request.getAmount())
                .operatorUserId(currentUser.getId())
                .operatorName(currentUser.getName())
                .build();

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            try {
                String originalFilename = invoiceFile.getOriginalFilename();
                String ext = originalFilename != null && originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : "";
                String storedName = UUID.randomUUID() + ext;
                Path uploadPath = Paths.get(uploadDir, "cost-adjustments");
                Files.createDirectories(uploadPath);
                Path targetFile = uploadPath.resolve(storedName);
                invoiceFile.transferTo(targetFile.toFile());

                adjustment.setInvoiceFileName(originalFilename);
                adjustment.setInvoiceFilePath(targetFile.toString());
                adjustment.setInvoiceContentType(invoiceFile.getContentType());
                adjustment.setInvoiceFileSize(invoiceFile.getSize());
            } catch (IOException e) {
                throw new BusinessException("发票文件上传失败: " + e.getMessage());
            }
        }

        costAdjustmentRepository.save(adjustment);
    }

    private boolean isAdmin(UserPrincipal user) {
        if (user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().trim())) {
            return true;
        }
        return getAdminUsernames().contains(user.getUsername().trim());
    }

    private static final String JIAOMIAO_ID = "000027";
    private static final String CHENLEI_ID = "000044";

    @Transactional
    public Map<String, Object> submitProjectExpense(String projectId, SubmitProjectExpenseRequest request, MultipartFile invoiceFile) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        ProjectExpenseType type;
        try {
            type = ProjectExpenseType.valueOf(request.getExpenseType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的费用类型: " + request.getExpenseType());
        }

        if (request.getItemName() == null || request.getItemName().isBlank()) {
            throw new BusinessException("名称不能为空");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(request.getAmount());
        } catch (NumberFormatException e) {
            throw new BusinessException("金额格式无效");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("金额必须大于0");
        }

        ProjectExpense expense = ProjectExpense.builder()
                .projectId(projectId)
                .projectName(project.getName())
                .expenseType(type)
                .itemName(request.getItemName().trim())
                .amount(amount)
                .submitterUserId(currentUser.getId())
                .submitterName(currentUser.getName())
                .status(ProjectExpenseStatus.PENDING_JIAOMIAO)
                .build();

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            try {
                String originalFilename = invoiceFile.getOriginalFilename();
                String ext = originalFilename != null && originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : "";
                String storedName = UUID.randomUUID() + ext;
                Path uploadPath = Paths.get(uploadDir, "project-expenses");
                Files.createDirectories(uploadPath);
                Path targetFile = uploadPath.resolve(storedName);
                invoiceFile.transferTo(targetFile.toFile());
                expense.setInvoiceFileName(originalFilename);
                expense.setInvoiceFilePath(targetFile.toString());
                expense.setInvoiceContentType(invoiceFile.getContentType());
                expense.setInvoiceFileSize(invoiceFile.getSize());
            } catch (IOException e) {
                throw new BusinessException("发票文件上传失败: " + e.getMessage());
            }
        }

        expenseRepository.save(expense);

        String typeLabel = switch (type) {
            case HARDWARE -> "硬件采购";
            case EXTERNAL_SERVICE -> "外部技术服务";
            case REIMBURSEMENT -> "报销";
        };
        internalMessageService.sendMessage(
                JIAOMIAO_ID,
                "EXPENSE_PENDING",
                "新的费用审批",
                project.getName() + " 提交了" + typeLabel + "费用：¥" + amount + "（" + expense.getItemName() + "），请审批。",
                projectId
        );

        return Map.of("id", expense.getId(), "message", "费用已提交，等待审批");
    }

    @Transactional
    public Map<String, Object> reviewExpense(Long expenseId, ReviewExpenseRequest request) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        String uid = currentUser.getId();
        if (!JIAOMIAO_ID.equals(uid) && !CHENLEI_ID.equals(uid)) {
            throw new PermissionDeniedException("仅审批人可操作");
        }
        boolean isJiaomiao = JIAOMIAO_ID.equals(uid);

        ProjectExpense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException("费用记录不存在"));

        String action = request.getAction();
        if (action == null || action.isBlank()) {
            throw new BusinessException("缺少操作类型");
        }
        action = action.trim().toUpperCase();

        switch (action) {
            case "APPROVE" -> handleApprove(expense, isJiaomiao);
            case "REJECT" -> handleReject(expense, isJiaomiao, request.getReason());
            case "REVOKE" -> handleRevoke(expense, isJiaomiao);
            default -> throw new BusinessException("无效操作: " + action);
        }

        expenseRepository.save(expense);
        return Map.of("id", expense.getId(), "status", expense.getStatus().name(), "message", "操作成功");
    }

    private void handleApprove(ProjectExpense expense, boolean isJiaomiao) {
        if (isJiaomiao) {
            if (expense.getJiaomiaoAction() != null && !"REJECT".equals(expense.getJiaomiaoAction())) {
                throw new BusinessException("焦淼已审批过此费用");
            }
            if (expense.getStatus() != ProjectExpenseStatus.PENDING_JIAOMIAO) {
                throw new BusinessException("当前状态不是待焦淼审批");
            }
            expense.setJiaomiaoAction("APPROVE");
            expense.setJiaomiaoAt(Instant.now());
            expense.setStatus(ProjectExpenseStatus.PENDING_CHENLEI);
            internalMessageService.sendMessage(
                    CHENLEI_ID,
                    "EXPENSE_PENDING",
                    "新的费用审批",
                    expense.getProjectName() + " 提交了费用：¥" + expense.getAmount() + "（" + expense.getItemName() + "），焦淼已通过，请您审批。",
                    expense.getProjectId()
            );
        } else {
            if (expense.getChenleiAction() != null && !"REJECT".equals(expense.getChenleiAction())) {
                throw new BusinessException("陈磊已审批过此费用");
            }
            if (expense.getStatus() != ProjectExpenseStatus.PENDING_CHENLEI) {
                throw new BusinessException("当前状态不是待陈磊审批");
            }
            expense.setChenleiAction("APPROVE");
            expense.setChenleiAt(Instant.now());
            expense.setStatus(ProjectExpenseStatus.APPROVED);

            SysProject project = projectRepository.findById(expense.getProjectId()).orElse(null);
            if (project != null) {
                BigDecimal prev = project.getCost() != null ? project.getCost() : BigDecimal.ZERO;
                project.setCost(prev.add(expense.getAmount()));
                projectRepository.save(project);
            }
            internalMessageService.sendMessage(
                    expense.getSubmitterUserId(),
                    "EXPENSE_APPROVED",
                    "费用审批通过",
                    expense.getProjectName() + " 的费用 ¥" + expense.getAmount() + "（" + expense.getItemName() + "）已审批通过，已计入项目成本。",
                    expense.getProjectId()
            );
        }
    }

    private void handleReject(ProjectExpense expense, boolean isJiaomiao, String reason) {
        if (isJiaomiao) {
            if (expense.getStatus() != ProjectExpenseStatus.PENDING_JIAOMIAO) {
                throw new BusinessException("当前状态不是待焦淼审批");
            }
            expense.setJiaomiaoAction("REJECT");
            expense.setJiaomiaoAt(Instant.now());
            expense.setRejectReason(reason);
            expense.setStatus(ProjectExpenseStatus.REJECTED);
            internalMessageService.sendMessage(
                    expense.getSubmitterUserId(),
                    "EXPENSE_REJECTED",
                    "费用审批被拒绝",
                    expense.getProjectName() + " 的费用 ¥" + expense.getAmount() + "（" + expense.getItemName() + "）被焦淼拒绝。原因：" + (reason != null ? reason : "无"),
                    expense.getProjectId()
            );
        } else {
            if (expense.getStatus() != ProjectExpenseStatus.PENDING_CHENLEI) {
                throw new BusinessException("当前状态不是待陈磊审批");
            }
            expense.setChenleiAction("REJECT");
            expense.setChenleiAt(Instant.now());
            expense.setRejectReason(reason);
            expense.setStatus(ProjectExpenseStatus.PENDING_JIAOMIAO);
            expense.setJiaomiaoAction(null);
            expense.setJiaomiaoAt(null);
            internalMessageService.sendMessage(
                    expense.getSubmitterUserId(),
                    "EXPENSE_REJECTED",
                    "费用被退回",
                    expense.getProjectName() + " 的费用 ¥" + expense.getAmount() + "（" + expense.getItemName() + "）被陈磊退回到焦淼重审。原因：" + (reason != null ? reason : "无"),
                    expense.getProjectId()
            );
            internalMessageService.sendMessage(
                    JIAOMIAO_ID,
                    "EXPENSE_PENDING",
                    "费用退回重新审批",
                    expense.getProjectName() + " 的费用 ¥" + expense.getAmount() + "（" + expense.getItemName() + "）被陈磊退回，请重新审批。",
                    expense.getProjectId()
            );
        }
    }

    private void handleRevoke(ProjectExpense expense, boolean isJiaomiao) {
        if (isJiaomiao) {
            if (expense.getJiaomiaoAction() == null || expense.getJiaomiaoAction().isBlank()) {
                throw new BusinessException("焦淼尚未审批，无法反审批");
            }
            if (expense.getStatus() == ProjectExpenseStatus.PENDING_JIAOMIAO) {
                throw new BusinessException("当前已是待焦淼审批状态");
            }
            if (expense.getChenleiAction() != null && !expense.getChenleiAction().isBlank()) {
                throw new BusinessException("陈磊已审批，无法单独反审批");
            }
            expense.setJiaomiaoAction(null);
            expense.setJiaomiaoAt(null);
            expense.setStatus(ProjectExpenseStatus.PENDING_JIAOMIAO);
        } else {
            if (expense.getChenleiAction() == null || expense.getChenleiAction().isBlank()) {
                throw new BusinessException("陈磊尚未审批，无法反审批");
            }
            if (expense.getStatus() == ProjectExpenseStatus.PENDING_CHENLEI) {
                throw new BusinessException("当前已是待陈磊审批状态");
            }
            expense.setChenleiAction(null);
            expense.setChenleiAt(null);
            if (expense.getJiaomiaoAction() != null && "APPROVE".equals(expense.getJiaomiaoAction())) {
                expense.setStatus(ProjectExpenseStatus.PENDING_CHENLEI);
            } else {
                expense.setStatus(ProjectExpenseStatus.PENDING_JIAOMIAO);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectExpense> getReviewableExpenses() {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) return List.of();
        String uid = currentUser.getId();
        if (!JIAOMIAO_ID.equals(uid) && !CHENLEI_ID.equals(uid)) return List.of();

        boolean isJiaomiao = JIAOMIAO_ID.equals(uid);
        if (isJiaomiao) {
            return expenseRepository.findByStatusInOrderByCreatedAtDesc(
                    List.of(ProjectExpenseStatus.PENDING_JIAOMIAO, ProjectExpenseStatus.PENDING_CHENLEI));
        }
        return expenseRepository.findByStatusOrderByCreatedAtDesc(ProjectExpenseStatus.PENDING_CHENLEI);
    }

    @Transactional(readOnly = true)
    public List<ProjectExpense> getReviewedHistory() {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) return List.of();
        String uid = currentUser.getId();
        if (!JIAOMIAO_ID.equals(uid) && !CHENLEI_ID.equals(uid)) return List.of();

        return expenseRepository.findAllReviewed();
    }

    @Transactional(readOnly = true)
    public List<ProjectCostAdjustment> getCostAdjustmentLog() {
        return costAdjustmentRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<FinanceCostBatch> getBatchLog() {
        return costBatchRepository.findAll();
    }

    private ProjectSubtaskResponse toSubtaskResponse(ProjectSubtask task) {
        return ProjectSubtaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .assigneeUserId(task.getAssigneeUserId())
                .assigneeName(task.getAssigneeName())
                .sortOrder(task.getSortOrder())
                .completed(Boolean.TRUE.equals(task.getCompleted()))
                .completedAt(task.getCompletedAt() != null ? DATE_FORMATTER.format(task.getCompletedAt()) : null)
                .build();
    }
}
