package com.smartlab.erp.service;

import com.smartlab.erp.dto.ResearchInitiateRequest;
import com.smartlab.erp.dto.ResearchStatusTransitionRequest;
import com.smartlab.erp.entity.*;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.repository.*;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.util.AuthUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResearchFlowService {
    private static final String RESEARCH_BLUEPRINT_DOC = "RESEARCH_BLUEPRINT_DOC";
    private static final String RESEARCH_ARCHITECTURE_DOC = "RESEARCH_ARCHITECTURE_DOC";
    private static final String RESEARCH_TASK_BREAKDOWN_DOC = "RESEARCH_TASK_BREAKDOWN_DOC";
    private static final String RESEARCH_EVALUATION_REPORT = "RESEARCH_EVALUATION_REPORT";
    private static final String UPLOAD_BASE_DIR = System.getProperty("user.dir") + "/uploads/research/";

    @Value("${auth.research-initiators:}")
    private String researchInitiatorsConfig;

    private final SysProjectRepository projectRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final ProjectMemberParticipationService projectMemberParticipationService;
    private final UserRepository userRepository;
    private final ResearchProjectProfileRepository researchProjectProfileRepository;
    private final ProjectAssetRepository projectAssetRepository;
    private final MiddlewareAssetRepository middlewareAssetRepository;
    private final MiddlewareRoyaltyRosterRepository middlewareRoyaltyRosterRepository;
    private final WorkflowMemberRoleSyncService workflowMemberRoleSyncService;

    private static final List<ResearchStatus> WORKFLOW_ORDER = List.of(
            ResearchStatus.INIT,
            ResearchStatus.BLUEPRINT,
            ResearchStatus.EXPANSION,
            ResearchStatus.DESIGN,
            ResearchStatus.EXECUTION,
            ResearchStatus.EVALUATION,
            ResearchStatus.ARCHIVE
    );

    @Transactional
    public SysProject initiateResearch(ResearchInitiateRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        assertResearchInitiator(currentUser);

        if (request == null) {
            throw new BusinessException("请求体不能为空");
        }
        if (isBlank(request.getIdea())) {
            throw new BusinessException("idea 不能为空");
        }
        if (isBlank(request.getInnovationPoint())) {
            throw new BusinessException("innovation 不能为空");
        }
        if (request.getBudget() == null) {
            throw new BusinessException("budget 不能为空");
        }

        User initiator = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BusinessException("当前用户不存在"));

        Set<String> memberIds = sanitizeUserIds(request.getCoreMemberIds());
        memberIds.add(initiator.getUserId());
        if (memberIds.size() < 2) {
            throw new BusinessException("成员数量必须 >= 2");
        }

        User host = resolveUser(request.getHostUserId(), initiator);
        User chiefEngineer = resolveUser(request.getChiefEngineerUserId(), initiator);

        String projectId = UUID.randomUUID().toString();
        SysProject project = SysProject.builder()
                .projectId(projectId)
                .name("科研流 - " + request.getIdea().trim())
                .description(request.getInnovationPoint())
                .manager(initiator)
                .projectType(ProjectType.AI_FOR_SCIENCE)
                .flowType(FlowType.RESEARCH)
                .projectStatus(ProjectStatus.INITIATED)
                .productStatus(ProductStatus.IDEA)
                .researchStatus(ResearchStatus.INIT)
                .budget(request.getBudget())
                .cost(BigDecimal.ZERO)
                .techStack("")
                .repoUrl("")
                .deployUrl("")
                .build();
        SysProject savedProject = projectRepository.save(project);

        ResearchProjectProfile profile = ResearchProjectProfile.builder()
                .projectId(projectId)
                .status(ResearchStatus.INIT)
                .innovationPoint(request.getInnovationPoint())
                .ideaText(request.getIdea())
                .budgetEstimate(request.getBudget())
                .ideaOwnerUserId(initiator.getUserId())
                .hostUserId(host.getUserId())
                .chiefEngineerUserId(chiefEngineer.getUserId())
                .blueprintOwnerUserId(host.getUserId())
                .architectureOwnerUserId(chiefEngineer.getUserId())
                .taskBreakdownOwnerUserId(chiefEngineer.getUserId())
                .evaluationReportOwnerUserId(chiefEngineer.getUserId())
                .workflowFlags("{}")
                .build();
        researchProjectProfileRepository.save(profile);

        upsertMemberRole(projectId, initiator, "RESEARCH");
        upsertMemberRole(projectId, host, "HOST");
        upsertMemberRole(projectId, chiefEngineer, "CHIEF_ENGINEER");
        for (String uid : memberIds) {
            User member = userRepository.findById(uid).orElseThrow(() -> new BusinessException("成员不存在: " + uid));
            if (!member.getUserId().equals(initiator.getUserId())
                    && !member.getUserId().equals(host.getUserId())
                    && !member.getUserId().equals(chiefEngineer.getUserId())) {
                upsertMemberRole(projectId, member, "MEMBER");
            }
        }

        log.info("[ResearchFlow] initiated project={}, owner={}, host={}, chief={}", projectId, initiator.getUserId(), host.getUserId(), chiefEngineer.getUserId());
        return savedProject;
    }

    private void assertResearchInitiator(UserPrincipal currentUser) {
        String role = currentUser.getRole() == null ? "" : currentUser.getRole().trim().toUpperCase(Locale.ROOT);
        if ("RESEARCH".equals(role)) {
            return;
        }

        String username = normalizeResearchInitiator(currentUser.getUsername());
        String name = normalizeResearchInitiator(currentUser.getName());
        Set<String> initiators = getResearchInitiators();
        if (initiators.contains(username) || initiators.contains(name)) {
            log.info("[ResearchFlow] allow whitelisted initiator username={}, name={}, role={}", username, name, role);
            return;
        }

        throw new PermissionDeniedException("权限不足：仅 RESEARCH 角色或授权白名单用户可以发起科研流项目");
    }

    private Set<String> getResearchInitiators() {
        if (isBlank(researchInitiatorsConfig)) {
            return Set.of();
        }
        return Arrays.stream(researchInitiatorsConfig.split(","))
                .map(this::normalizeResearchInitiator)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizeResearchInitiator(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public Map<String, Object> transitionStatus(String projectId, ResearchStatusTransitionRequest request) {
        UserPrincipal currentUser = requireCurrentUser();
        if (request == null || isBlank(request.getToStatus())) {
            throw new BusinessException("toStatus 不能为空");
        }

        ResearchStatus toStatus = parseStatus(request.getToStatus());
        SysProject project = requireResearchProject(projectId);
        ResearchProjectProfile profile = requireResearchProfile(projectId);
        ResearchStatus current = normalizeStatus(profile.getStatus());
        String uid = currentUser.getId();

        if (toStatus == ResearchStatus.SHELVED) {
            requireAnyRole(projectId, uid, Set.of("RESEARCH", "HOST", "CHIEF_ENGINEER"));
            applyStatus(project, profile, toStatus);
            return Map.of("success", true, "from", current.name(), "to", toStatus.name());
        }

        if (isRollback(current, toStatus)) {
            requireRole(projectId, uid, "CHIEF_ENGINEER", "仅总工程师可回滚阶段");
            if (!Boolean.TRUE.equals(request.getCriticalIssue()) && !Boolean.TRUE.equals(request.getIntegrationFailed())) {
                throw new BusinessException("回滚需要 criticalIssue 或 integrationFailed 条件");
            }
            applyStatus(project, profile, toStatus);
            return Map.of("success", true, "from", current.name(), "to", toStatus.name(), "rollback", true);
        }

        if (!isNextStep(current, toStatus)) {
            throw new BusinessException("仅允许单向递进到下一阶段");
        }

        switch (current) {
            case INIT -> {
                requireInitiator(profile, uid, "INIT -> BLUEPRINT 仅发起人可操作");
                validateInitToBlueprint(projectId, profile);
            }
            case BLUEPRINT -> {
                requireRole(projectId, uid, "HOST", "BLUEPRINT -> EXPANSION 仅主持人可操作");
                if (!Boolean.TRUE.equals(request.getBlueprintExists()) || !Boolean.TRUE.equals(request.getSmallGroupAllConfirmed())) {
                    throw new BusinessException("需存在初步蓝图且小群成员全部确认");
                }
            }
            case EXPANSION -> {
                boolean hostPath = hasRole(projectId, uid, "HOST") && Boolean.TRUE.equals(request.getTaskPlanDefined()) && Boolean.TRUE.equals(request.getResearchTasksAssigned());
                double threshold = request.getVotePassThreshold() == null ? 0.67 : request.getVotePassThreshold();
                boolean votePath = request.getVotePassRate() != null && request.getVotePassRate() >= threshold;
                if (!hostPath && !votePath) {
                    throw new BusinessException("需满足主持人推进条件或投票通过率门槛");
                }
            }
            case DESIGN -> {
                requireRole(projectId, uid, "CHIEF_ENGINEER", "DESIGN -> EXECUTION 仅总工程师可操作");
                if (!Boolean.TRUE.equals(request.getArchitectureDefined()) || !Boolean.TRUE.equals(request.getTechRouteDefined()) || !Boolean.TRUE.equals(request.getTaskBreakdownComplete())) {
                    throw new BusinessException("需完成架构、技术路线、任务分解");
                }
                if (!isBlank(request.getChiefEngineerUserId())) {
                    User chief = userRepository.findById(request.getChiefEngineerUserId())
                            .orElseThrow(() -> new BusinessException("总工程师不存在: " + request.getChiefEngineerUserId()));
                    upsertMemberRole(projectId, chief, "CHIEF_ENGINEER");
                    profile.setChiefEngineerUserId(chief.getUserId());
                    profile.setArchitectureOwnerUserId(chief.getUserId());
                    profile.setTaskBreakdownOwnerUserId(chief.getUserId());
                    profile.setEvaluationReportOwnerUserId(chief.getUserId());
                }
                if (isBlank(profile.getChiefEngineerUserId())) {
                    throw new BusinessException("必须指定总工程师");
                }
                requireResearchAsset(projectId, RESEARCH_ARCHITECTURE_DOC, "进入施工执行前，需由责任人上传架构文档");
                requireResearchAsset(projectId, RESEARCH_TASK_BREAKDOWN_DOC, "进入施工执行前，需由责任人上传任务分解文档");
            }
            case EXECUTION -> {
                requireRole(projectId, uid, "CHIEF_ENGINEER", "EXECUTION -> EVALUATION 仅总工程师可操作");
                boolean modeA = Boolean.TRUE.equals(request.getAllModulesCompleted()) && Boolean.TRUE.equals(request.getIntegrationSuccess());
                boolean modeB = Boolean.TRUE.equals(request.getCurrentVersionStable()) && Boolean.TRUE.equals(request.getMajorTasksCompleted());
                if (!modeA && !modeB) {
                    throw new BusinessException("需满足并行缝合或迭代稳定任一条件");
                }
            }
            case EVALUATION -> {
                requireAnyRole(projectId, uid, Set.of("CHIEF_ENGINEER", "HOST", "RESEARCH"));
                String result = String.valueOf(request.getEvaluationResult() == null ? "" : request.getEvaluationResult()).trim().toLowerCase(Locale.ROOT);
                if (!Boolean.TRUE.equals(request.getEvaluationCompleted()) || (!"accepted".equals(result) && !"usable".equals(result))) {
                    throw new BusinessException("评测需完成且结果为 accepted/usable 才可归档");
                }
                requireResearchAsset(projectId, RESEARCH_EVALUATION_REPORT, "入库前，需由责任人上传评测报告");
            }
            default -> throw new BusinessException("当前状态不支持流转: " + current);
        }

        applyStatus(project, profile, toStatus);
        return Map.of("success", true, "from", current.name(), "to", toStatus.name());
    }

    @Transactional
    public void setConstructionMode(String projectId, String executionMode) {
        UserPrincipal currentUser = requireCurrentUser();
        requireRole(projectId, currentUser.getId(), "CHIEF_ENGINEER", "仅总工程师可设置执行模式");
        String normalized = normalizeExecutionMode(executionMode);
        ResearchProjectProfile profile = requireResearchProfile(projectId);
        profile.setExecutionMode(normalized);
        researchProjectProfileRepository.save(profile);
    }

    @Transactional
    public MiddlewareAsset archiveToMiddleware(String projectId,
                                               String middlewareName,
                                               String middlewareDesc,
                                               String repoUrl) {
        UserPrincipal currentUser = requireCurrentUser();
        requireRole(projectId, currentUser.getId(), "CHIEF_ENGINEER", "只有总工程师可执行中间件入库");
        if (isBlank(middlewareName)) {
            throw new BusinessException("middlewareName 不能为空");
        }

        SysProject project = requireResearchProject(projectId);
        ResearchProjectProfile profile = requireResearchProfile(projectId);
        ResearchStatus status = normalizeStatus(profile.getStatus());
        if (status != ResearchStatus.ARCHIVE) {
            throw new BusinessException("仅 ARCHIVE 阶段可执行中间件入库");
        }

        MiddlewareAsset asset = MiddlewareAsset.builder()
                .name(middlewareName.trim())
                .description(middlewareDesc)
                .sourceProjectId(projectId)
                .sourceFlowType(FlowType.RESEARCH)
                .sourceStatus(status.name())
                .ownerUserId(profile.getChiefEngineerUserId())
                .repoUrl(repoUrl)
                .rating("A")
                .pricingModel("INTERNAL")
                .unitPrice(BigDecimal.ZERO)
                .internalCostPrice(BigDecimal.ZERO)
                .marketReferencePrice(BigDecimal.ZERO)
                .currency("CNY")
                .billingUnit("PROJECT")
                .versionTag("v1.0.0")
                .lifecycleStatus("ACTIVE")
                .build();
        MiddlewareAsset savedAsset = middlewareAssetRepository.save(asset);

        lockRoyaltyRoster(savedAsset, project, profile.getChiefEngineerUserId());
        return savedAsset;
    }

    @Transactional
    public Map<String, Object> uploadResearchKeyDoc(String projectId, MultipartFile file, String docType) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        if (project.getFlowType() != FlowType.RESEARCH) {
            throw new BusinessException("非科研流项目无法执行关键文档上传");
        }

        Set<String> validDocTypes = Set.of(RESEARCH_BLUEPRINT_DOC, RESEARCH_ARCHITECTURE_DOC,
                RESEARCH_TASK_BREAKDOWN_DOC, RESEARCH_EVALUATION_REPORT);
        if (!validDocTypes.contains(docType)) {
            throw new BusinessException("非法的文档类型: " + docType);
        }

        ResearchProjectProfile profile = researchProjectProfileRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("科研项目配置不存在"));

        String expectedUploaderId = getResearchDocOwnerUserId(profile, docType);
        if (expectedUploaderId == null || expectedUploaderId.isBlank()) {
            throw new BusinessException(getDocTypeLabel(docType) + " 尚未指定责任人");
        }
        if (!expectedUploaderId.equals(currentUser.getId())) {
            throw new PermissionDeniedException(getDocTypeLabel(docType) + " 必须由指定责任人上传");
        }

        ResearchStatus currentStatus = profile.getStatus();
        ResearchStatus requiredStatus = getDocTypeRequiredStatus(docType);
        if (currentStatus != requiredStatus) {
            throw new BusinessException(getDocTypeLabel(docType) + " 仅在 " + requiredStatus.name() + " 阶段允许上传，当前阶段: " + currentStatus.name());
        }

        String uploadDir = UPLOAD_BASE_DIR + projectId + "/" + docType + "/";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "doc_" + System.currentTimeMillis();
        }
        String fileType = "FILE";
        if (originalFilename.contains(".")) {
            fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toUpperCase();
        }
        String savedPath;
        try {
            File directory = new File(uploadDir);
            if (!directory.exists()) directory.mkdirs();
            Path targetPath = Paths.get(uploadDir + originalFilename);
            file.transferTo(targetPath);
            savedPath = targetPath.toString();
        } catch (IOException e) {
            throw new BusinessException("文档存储失败: " + e.getMessage());
        }

        User uploader = userRepository.findById(currentUser.getId()).orElse(null);
        ProjectAsset asset = ProjectAsset.builder()
                .project(project)
                .fileName(originalFilename)
                .fileType(fileType)
                .filePath(savedPath)
                .fileData(readFileBytes(file))
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .uploaderName(uploader != null ? uploader.getName() : "Unknown")
                .uploadedAt(Instant.now())
                .assetCategory(docType)
                .build();
        projectAssetRepository.save(asset);

        boolean autoTransition = false;
        String fromStatus = currentStatus.name();
        String toStatus = null;

        if (currentStatus == ResearchStatus.INIT && RESEARCH_BLUEPRINT_DOC.equals(docType)) {
            project.setResearchStatus(ResearchStatus.BLUEPRINT);
            projectRepository.save(project);
            profile.setStatus(ResearchStatus.BLUEPRINT);
            researchProjectProfileRepository.save(profile);
            autoTransition = true;
            toStatus = ResearchStatus.BLUEPRINT.name();
            log.info("[ResearchFlow] 项目 {} 上传蓝图文档，自动推进至 BLUEPRINT", projectId);
        } else if (currentStatus == ResearchStatus.BLUEPRINT && RESEARCH_ARCHITECTURE_DOC.equals(docType)) {
            project.setResearchStatus(ResearchStatus.EXPANSION);
            projectRepository.save(project);
            profile.setStatus(ResearchStatus.EXPANSION);
            researchProjectProfileRepository.save(profile);
            autoTransition = true;
            toStatus = ResearchStatus.EXPANSION.name();
            log.info("[ResearchFlow] 项目 {} 上传架构文档，自动推进至 EXPANSION", projectId);
        } else if (currentStatus == ResearchStatus.EXPANSION && RESEARCH_TASK_BREAKDOWN_DOC.equals(docType)) {
            project.setResearchStatus(ResearchStatus.DESIGN);
            projectRepository.save(project);
            profile.setStatus(ResearchStatus.DESIGN);
            researchProjectProfileRepository.save(profile);
            autoTransition = true;
            toStatus = ResearchStatus.DESIGN.name();
            log.info("[ResearchFlow] 项目 {} 上传任务分解文档，自动推进至 DESIGN", projectId);
        } else if (currentStatus == ResearchStatus.DESIGN && RESEARCH_EVALUATION_REPORT.equals(docType)) {
            project.setResearchStatus(ResearchStatus.EXECUTION);
            projectRepository.save(project);
            profile.setStatus(ResearchStatus.EXECUTION);
            researchProjectProfileRepository.save(profile);
            autoTransition = true;
            toStatus = ResearchStatus.EXECUTION.name();
            log.info("[ResearchFlow] 项目 {} 上传评测报告，自动推进至 EXECUTION", projectId);
        }

        return Map.of(
                "success", true,
                "fileName", originalFilename,
                "docType", docType,
                "autoTransition", autoTransition,
                "fromStatus", fromStatus,
                "toStatus", toStatus != null ? toStatus : "",
                "currentStatus", project.getResearchStatus().name()
        );
    }

    private String getResearchDocOwnerUserId(ResearchProjectProfile profile, String docType) {
        return switch (docType) {
            case RESEARCH_BLUEPRINT_DOC -> sanitizeUserId(profile.getBlueprintOwnerUserId());
            case RESEARCH_ARCHITECTURE_DOC -> sanitizeUserId(profile.getArchitectureOwnerUserId());
            case RESEARCH_TASK_BREAKDOWN_DOC -> sanitizeUserId(profile.getTaskBreakdownOwnerUserId());
            case RESEARCH_EVALUATION_REPORT -> sanitizeUserId(profile.getEvaluationReportOwnerUserId());
            default -> null;
        };
    }

    private String getDocTypeLabel(String docType) {
        return switch (docType) {
            case RESEARCH_BLUEPRINT_DOC -> "蓝图文档";
            case RESEARCH_ARCHITECTURE_DOC -> "架构文档";
            case RESEARCH_TASK_BREAKDOWN_DOC -> "任务分解文档";
            case RESEARCH_EVALUATION_REPORT -> "评测报告";
            default -> docType;
        };
    }

    private ResearchStatus getDocTypeRequiredStatus(String docType) {
        return switch (docType) {
            case RESEARCH_BLUEPRINT_DOC -> ResearchStatus.INIT;
            case RESEARCH_ARCHITECTURE_DOC -> ResearchStatus.BLUEPRINT;
            case RESEARCH_TASK_BREAKDOWN_DOC -> ResearchStatus.EXPANSION;
            case RESEARCH_EVALUATION_REPORT -> ResearchStatus.DESIGN;
            default -> throw new BusinessException("非法的文档类型: " + docType);
        };
    }

    private String sanitizeUserId(String userId) {
        if (userId == null) {
            return null;
        }
        String trimmed = userId.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("文件内容读取失败: " + e.getMessage());
        }
    }

    private void validateInitToBlueprint(String projectId, ResearchProjectProfile profile) {
        if (profile.getBudgetEstimate() == null || isBlank(profile.getIdeaText()) || isBlank(profile.getInnovationPoint())) {
            throw new BusinessException("INIT -> BLUEPRINT 需填写 budget/idea/innovation");
        }
        int members = projectMemberRepository.findByProjectId(projectId).size();
        if (members < 2) {
            throw new BusinessException("INIT -> BLUEPRINT 需成员数量 >= 2");
        }
        requireResearchAsset(projectId, RESEARCH_BLUEPRINT_DOC, "进入蓝图阶段前，需由责任人上传蓝图文档");
    }

    private void requireResearchAsset(String projectId, String assetCategory, String message) {
        boolean exists = projectAssetRepository.findByProjectProjectIdOrderByUploadedAtDesc(projectId).stream()
                .anyMatch(asset -> assetCategory.equalsIgnoreCase(String.valueOf(asset.getAssetCategory())));
        if (!exists) {
            throw new BusinessException(message);
        }
    }

    private void lockRoyaltyRoster(MiddlewareAsset asset, SysProject project, String chiefEngineerUserId) {
        Map<String, Double> weightMap = new LinkedHashMap<>();
        String managerId = project.getManager() != null ? project.getManager().getUserId() : null;
        if (managerId != null) {
            weightMap.merge(managerId, 0.35, Double::sum);
        }
        if (!isBlank(chiefEngineerUserId)) {
            weightMap.merge(chiefEngineerUserId, 0.35, Double::sum);
        }

        List<String> memberUserIds = projectMemberRepository.findByProjectId(project.getProjectId()).stream()
                .filter(member -> "MEMBER".equals(member.getRole()))
                .map(member -> member.getUser().getUserId())
                .distinct()
                .toList();
        if (!memberUserIds.isEmpty()) {
            double each = 0.30 / memberUserIds.size();
            memberUserIds.forEach(uid -> weightMap.merge(uid, each, Double::sum));
        }

        double total = weightMap.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            throw new BusinessException("无法生成分润名册：总权重为0");
        }

        List<MiddlewareRoyaltyRoster> rosterList = weightMap.entrySet().stream()
                .map(entry -> MiddlewareRoyaltyRoster.builder()
                        .middleware(asset)
                        .userId(entry.getKey())
                        .royaltyRatio(BigDecimal.valueOf(entry.getValue() / total).setScale(4, RoundingMode.HALF_UP))
                        .build())
                .collect(Collectors.toList());
        middlewareRoyaltyRosterRepository.saveAll(rosterList);
    }

    private void upsertMemberRole(String projectId, User user, String role) {
        Optional<SysProjectMember> existing = projectMemberRepository.findByProjectIdAndUserUserId(projectId, user.getUserId());
        if (existing.isPresent()) {
            SysProjectMember member = existing.get();
            member.setRole(role);
            projectMemberRepository.save(member);
            workflowMemberRoleSyncService.sync(FlowType.RESEARCH.name(), projectId, user.getUserId(), member.getRole());
            projectMemberParticipationService.recordJoin(projectId, user, member.getJoinedAt());
            return;
        }
        SysProjectMember member = SysProjectMember.builder()
                .projectId(projectId)
                .user(user)
                .role(role)
                .weight(0)
                .build();
        projectMemberRepository.save(member);
        workflowMemberRoleSyncService.sync(FlowType.RESEARCH.name(), projectId, user.getUserId(), member.getRole());
        projectMemberParticipationService.recordJoin(projectId, user, member.getJoinedAt());
    }

    private User resolveUser(String userId, User fallback) {
        if (isBlank(userId)) return fallback;
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在: " + userId));
    }

    private Set<String> sanitizeUserIds(List<String> ids) {
        if (ids == null) return new LinkedHashSet<>();
        return ids.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isRollback(ResearchStatus current, ResearchStatus target) {
        return (current == ResearchStatus.EXECUTION && target == ResearchStatus.DESIGN)
                || (current == ResearchStatus.EVALUATION && target == ResearchStatus.EXECUTION)
                || (current == ResearchStatus.DESIGN && target == ResearchStatus.EXPANSION);
    }

    private boolean isNextStep(ResearchStatus current, ResearchStatus target) {
        int currentIndex = WORKFLOW_ORDER.indexOf(current);
        int targetIndex = WORKFLOW_ORDER.indexOf(target);
        return currentIndex >= 0 && targetIndex == currentIndex + 1;
    }

    private ResearchStatus parseStatus(String value) {
        try {
            return normalizeStatus(ResearchStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (Exception ex) {
            throw new BusinessException("非法科研状态: " + value);
        }
    }

    private ResearchStatus normalizeStatus(ResearchStatus status) {
        if (status == null) return ResearchStatus.INIT;
        return switch (status) {
            case PROBE -> ResearchStatus.INIT;
            case DEEPENING -> ResearchStatus.EXPANSION;
            case PRE_EXECUTION -> ResearchStatus.DESIGN;
            case CONSTRUCTION -> ResearchStatus.EXECUTION;
            case ARCHIVED_TO_MIDDLEWARE -> ResearchStatus.ARCHIVE;
            default -> status;
        };
    }

    private void applyStatus(SysProject project, ResearchProjectProfile profile, ResearchStatus status) {
        project.setResearchStatus(status);
        profile.setStatus(status);
        if (status == ResearchStatus.ARCHIVE || status == ResearchStatus.SHELVED) {
            project.setEndDate(Instant.now());
        }
        projectRepository.save(project);
        researchProjectProfileRepository.save(profile);
    }

    private String normalizeExecutionMode(String executionMode) {
        String normalized = executionMode == null ? "" : executionMode.trim().toUpperCase(Locale.ROOT);
        if (!"MODE_A_PARALLEL".equals(normalized) && !"MODE_B_ITERATIVE".equals(normalized)) {
            throw new BusinessException("executionMode 仅允许 MODE_A_PARALLEL / MODE_B_ITERATIVE");
        }
        return normalized;
    }

    private SysProject requireResearchProject(String projectId) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));
        if (project.getFlowType() != FlowType.RESEARCH) {
            throw new BusinessException("仅科研流项目支持该操作");
        }
        return project;
    }

    private ResearchProjectProfile requireResearchProfile(String projectId) {
        return researchProjectProfileRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("科研扩展信息不存在: " + projectId));
    }

    private void requireRole(String projectId, String userId, String role, String message) {
        if (!hasRole(projectId, userId, role)) {
            throw new PermissionDeniedException(message);
        }
    }

    private void requireInitiator(ResearchProjectProfile profile, String userId, String message) {
        String initiatorId = profile == null ? null : profile.getIdeaOwnerUserId();
        if (initiatorId == null || initiatorId.isBlank() || !initiatorId.equals(userId)) {
            throw new PermissionDeniedException(message);
        }
    }

    private void requireAnyRole(String projectId, String userId, Set<String> roles) {
        SysProjectMember member = projectMemberRepository.findByProjectIdAndUserUserId(projectId, userId)
                .orElseThrow(() -> new PermissionDeniedException("您不是该科研项目成员"));
        if (!roles.contains(member.getRole())) {
            throw new PermissionDeniedException("无权限执行该科研流操作");
        }
    }

    private boolean hasRole(String projectId, String userId, String role) {
        return projectMemberRepository.findByProjectIdAndUserUserId(projectId, userId)
                .map(member -> role.equals(member.getRole()))
                .orElse(false);
    }

    private UserPrincipal requireCurrentUser() {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        return currentUser;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
