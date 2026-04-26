package com.smartlab.erp.service;

import com.smartlab.erp.dto.ProductIdeaRequest;
import com.smartlab.erp.dto.ProductMemberUpdateRequest;
import com.smartlab.erp.dto.ProductPromotionSetupRequest;
import com.smartlab.erp.dto.ProductTaskAssignmentDTO;
import com.smartlab.erp.dto.ProductTaskAssignmentUpdateRequest;
import com.smartlab.erp.entity.*;
import com.smartlab.erp.enums.DemoFileType;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.finance.entity.FinanceWalletAccount;
import com.smartlab.erp.finance.entity.FinanceWalletTransaction;
import com.smartlab.erp.finance.enums.FinanceCashFlowDirection;
import com.smartlab.erp.finance.enums.FinanceWalletTransactionType;
import com.smartlab.erp.finance.repository.FinanceWalletAccountRepository;
import com.smartlab.erp.finance.repository.FinanceWalletTransactionRepository;
import com.smartlab.erp.finance.service.FinanceReferenceService;
import com.smartlab.erp.finance.support.FinanceAmounts;
import com.smartlab.erp.repository.ProductIdeaDetailRepository;
import com.smartlab.erp.repository.ProjectMemberScheduleRepository;
import com.smartlab.erp.repository.ProjectAssetRepository;
import com.smartlab.erp.repository.ProjectChatMessageRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.util.AuthUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 产品流（软件流）主业务服务
 * 负责 Idea → 推广组队 → Demo 实施 → 会议决策 → 测试/上线 的核心编排。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductFlowService {

    private final SysProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final ProductIdeaDetailRepository productIdeaDetailRepository;
    private final ProjectMemberScheduleRepository projectMemberScheduleRepository;
    private final ProjectMemberParticipationService projectMemberParticipationService;
    private final InternalMessageService internalMessageService;
    private final ProjectAssetRepository projectAssetRepository;
    private final ProjectChatMessageRepository projectChatMessageRepository;
    private final FinanceReferenceService financeReferenceService;
    private final FinanceWalletAccountRepository walletAccountRepository;
    private final FinanceWalletTransactionRepository walletTransactionRepository;
    private final WorkflowMemberRoleSyncService workflowMemberRoleSyncService;
    private static final int MIN_TESTERS = 5;
    private static final BigDecimal PROMOTION_EXPENSE_RATE = new BigDecimal("0.05");
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    // === Task 2: 阶段 1 - Idea 创意孵化 =====================================

    /**
     * 创建产品 Idea（内部创投入口）
     * POST /api/products/idea
     */
    @Transactional
    public SysProject createIdea(ProductIdeaRequest request) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        validateIdeaRequest(request);

        User manager = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BusinessException("当前用户不存在"));

        String projectId = UUID.randomUUID().toString();

        SysProject project = SysProject.builder()
                .projectId(projectId)
                .name(request.getName())
                .description(request.getDescription())
                .manager(manager)
                .projectType(request.getProjectType())
                .flowType(FlowType.PRODUCT)
                .projectStatus(ProjectStatus.LEAD) // 轨道 B 主要看 productStatus
                .productStatus(ProductStatus.IDEA)
                .budget(request.getExpectedBudget())
                .cost(null)
                .techStack("")
                .repoUrl("")
                .deployUrl("")
                .build();

        SysProject saved = projectRepository.save(project);

        // 写入扩展表 ProductIdeaDetail（弱关联）
        ProductIdeaDetail detail = ProductIdeaDetail.builder()
                .projectId(projectId)
                .targetUsers(request.getTargetUsers())
                .coreFeatures(request.getCoreFeatures())
                .useCase(request.getUseCase())
                .problemStatement(request.getProblemStatement())
                .techStackDesc(request.getTechStackDesc())
                .ideaOwnerUserId(manager.getUserId())
                .build();
        productIdeaDetailRepository.save(detail);

        // 同时将 Idea 主理人作为成员写入成员表（ROLE: ADMIN）
        if (!projectMemberRepository.existsByProjectIdAndUserUserId(projectId, manager.getUserId())) {
            SysProjectMember member = SysProjectMember.builder()
                    .projectId(projectId)
                    .user(manager)
                    .role("ADMIN")
                    .weight(0)
                    .build();
            projectMemberRepository.save(member);
            workflowMemberRoleSyncService.sync(FlowType.PRODUCT.name(), projectId, manager.getUserId(), member.getRole());
            projectMemberParticipationService.recordJoin(projectId, manager, member.getJoinedAt());
        }
        ensureScheduleSlot(projectId, manager.getUserId());

        log.info("[ProductFlow] 新建 Idea 项目 {}, 主理人 {}", projectId, manager.getUserId());
        return saved;
    }

    // === Task 3: 阶段 2 - 推广与 Demo 组队 ==================================

    /**
     * 推广与 Demo 组队
     * POST /api/products/{projectId}/promotion-setup
     */
    @Transactional
    public SysProject setupPromotionTeam(String projectId, ProductPromotionSetupRequest request) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        // 仅 Idea 主理人 (manager) 可调用
        if (project.getManager() == null ||
                !project.getManager().getUserId().equals(currentUser.getId())) {
            throw new PermissionDeniedException("仅 Idea 主理人可以配置推广与 Demo 团队");
        }

        if (project.getFlowType() != FlowType.PRODUCT) {
            throw new BusinessException("该项目不是产品流项目，无法执行推广组队操作");
        }

        if (project.getProductStatus() != ProductStatus.IDEA) {
            throw new BusinessException("只有在 IDEA 阶段才能进行推广与 Demo 组队");
        }

        if (request.getPromotionMemberIds() == null || request.getPromotionMemberIds().isEmpty()) {
            throw new BusinessException("promotionMemberIds 不能为空");
        }
        if (request.getPromotionMemberIds().size() < 2) {
            throw new BusinessException("推广阶段至少需要 2 人参与");
        }
        if (request.getDemoEngineerIds() == null || request.getDemoEngineerIds().size() != 4) {
            throw new BusinessException("demoEngineerIds 需要精确包含 4 名工程师");
        }
        if (request.getProjectTier() == null) {
            throw new BusinessException("推广阶段必须完成项目评级");
        }
        if (request.getProjectType() == null) {
            throw new BusinessException("推广阶段必须选择行业分类");
        }

        Map<DemoFileType, String> demoOwners = new EnumMap<>(DemoFileType.class);
        demoOwners.put(DemoFileType.ENGINEERING, sanitizeSelectionId(request.getDemoEngineeringOwnerUserId()));
        demoOwners.put(DemoFileType.DEMO_FILE, sanitizeSelectionId(request.getDemoFileOwnerUserId()));
        demoOwners.put(DemoFileType.DESCRIPTION, sanitizeSelectionId(request.getDemoDescriptionOwnerUserId()));
        demoOwners.put(DemoFileType.FEASIBILITY, sanitizeSelectionId(request.getDemoFeasibilityOwnerUserId()));
        if (demoOwners.values().stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new BusinessException("进入 Demo 阶段前，必须为四类 Demo 文件分别指定责任人");
        }
        Set<String> demoEngineerIds = sanitizeSelectionIds(request.getDemoEngineerIds());
        for (Map.Entry<DemoFileType, String> entry : demoOwners.entrySet()) {
            if (!demoEngineerIds.contains(entry.getValue())) {
                throw new BusinessException("" + formatDemoFileType(entry.getKey()) + " 的责任人必须来自 Demo 工程师名单");
            }
        }

        Set<String> promotionMemberIds = sanitizeSelectionIds(request.getPromotionMemberIds());
        if (!promotionMemberIds.contains(currentUser.getId())) {
            throw new BusinessException("推广成员必须包含 Idea 主理人");
        }

        // 推广负责 IC
        String promotionIcUserId = sanitizeSelectionId(request.getPromotionIcUserId());
        User promotionIc = userRepository.findById(promotionIcUserId)
                .orElseThrow(() -> new BusinessException("推广负责 IC 用户不存在"));
        addMemberIfAbsent(projectId, promotionIc, "PROMOTION_IC");

        // 其他推广成员
        for (String uid : promotionMemberIds) {
            User u = userRepository.findById(uid)
                    .orElseThrow(() -> new BusinessException("推广成员不存在: " + uid));
            addMemberIfAbsent(projectId, u, "PROMOTION");
        }

        // Demo 工程师（4名）
        for (String uid : demoEngineerIds) {
            User u = userRepository.findById(uid)
                    .orElseThrow(() -> new BusinessException("Demo 工程师不存在: " + uid));
            addMemberIfAbsent(projectId, u, "DEMO_ENG");
        }

        // 状态双连跳：IDEA -> PROMOTION -> DEMO_EXECUTION
        project.setProjectTier(request.getProjectTier());
        project.setProjectType(request.getProjectType());
        project.setProductStatus(ProductStatus.PROMOTION);
        project.setProductStatus(ProductStatus.DEMO_EXECUTION);
        SysProject saved = projectRepository.save(project);

        ProductIdeaDetail detail = productIdeaDetailRepository.findByProjectId(projectId)
                .orElse(ProductIdeaDetail.builder().projectId(projectId).ideaOwnerUserId(project.getManager().getUserId()).build());
        detail.setPromotionIcUserId(promotionIc.getUserId());
        detail.setDemoEngineeringOwnerUserId(demoOwners.get(DemoFileType.ENGINEERING));
        detail.setDemoFileOwnerUserId(demoOwners.get(DemoFileType.DEMO_FILE));
        detail.setDemoDescriptionOwnerUserId(demoOwners.get(DemoFileType.DESCRIPTION));
        detail.setDemoFeasibilityOwnerUserId(demoOwners.get(DemoFileType.FEASIBILITY));
        productIdeaDetailRepository.save(detail);

        log.info("[ProductFlow] 项目 {} 推广与 Demo 组队完成，状态已跃迁至 DEMO_EXECUTION", projectId);
        return saved;
    }

    private void addMemberIfAbsent(String projectId, User user, String role) {
        if (!projectMemberRepository.existsByProjectIdAndUserUserId(projectId, user.getUserId())) {
            SysProjectMember member = SysProjectMember.builder()
                    .projectId(projectId)
                    .user(user)
                    .role(role)
                    .weight(0)
                    .build();
            projectMemberRepository.save(member);
            workflowMemberRoleSyncService.sync(FlowType.PRODUCT.name(), projectId, user.getUserId(), role);
            projectMemberParticipationService.recordJoin(projectId, user, member.getJoinedAt());
        }
        projectMemberRepository.findByProjectIdAndUserUserId(projectId, user.getUserId())
                .ifPresent(member -> projectMemberParticipationService.recordJoin(projectId, user, member.getJoinedAt()));
        ensureScheduleSlot(projectId, user.getUserId());
    }

    @Transactional
    public Map<String, Object> updateProductMembers(String projectId, ProductMemberUpdateRequest request) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        if (project.getFlowType() != FlowType.PRODUCT) {
            throw new BusinessException("仅产品流项目支持成员动态调整");
        }

        ProductIdeaDetail detail = productIdeaDetailRepository.findByProjectId(projectId)
                .orElse(ProductIdeaDetail.builder().projectId(projectId).build());
        String ideaOwnerUserId = detail.getIdeaOwnerUserId() != null && !detail.getIdeaOwnerUserId().isBlank()
                ? detail.getIdeaOwnerUserId()
                : (project.getManager() != null ? project.getManager().getUserId() : null);

        String currentUserId = currentUser.getId();
        String managerUserId = project.getManager() != null ? project.getManager().getUserId() : "";
        boolean canManage = currentUserId.equals(managerUserId) || (ideaOwnerUserId != null && ideaOwnerUserId.equals(currentUserId));
        if (!canManage) {
            throw new PermissionDeniedException("仅 Manager 或发起主理人可调整成员");
        }

        Set<String> addUserIds = sanitizeSelectionIds(request != null ? request.getAddUserIds() : null);
        Set<String> removeUserIds = sanitizeSelectionIds(request != null ? request.getRemoveUserIds() : null);

        if (ideaOwnerUserId != null && removeUserIds.contains(ideaOwnerUserId)) {
            throw new BusinessException("发起者不能被删除");
        }

        int added = 0;
        int removed = 0;

        for (String userId : addUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在: " + userId));
            if (!projectMemberRepository.existsByProjectIdAndUserUserId(projectId, userId)) {
                SysProjectMember member = SysProjectMember.builder()
                        .projectId(projectId)
                        .user(user)
                        .role("MEMBER")
                        .weight(0)
                        .build();
                projectMemberRepository.save(member);
                workflowMemberRoleSyncService.sync(FlowType.PRODUCT.name(), projectId, userId, member.getRole());
                projectMemberParticipationService.recordJoin(projectId, user, member.getJoinedAt());
                ensureScheduleSlot(projectId, userId);
                added++;
            }
        }

        for (String userId : removeUserIds) {
            projectMemberParticipationService.recordLeave(projectId, userId, Instant.now());
            removed += projectMemberRepository.deleteByProjectIdAndUserUserId(projectId, userId);
            projectMemberScheduleRepository.deleteByProjectIdAndUserId(projectId, userId);
        }

        ensureScheduleSlotsForMembers(projectId);

        return Map.of(
                "success", true,
                "added", added,
                "removed", removed,
                "ideaOwnerUserId", ideaOwnerUserId
        );
    }

    @Transactional
    public List<ProductTaskAssignmentDTO> getTaskAssignments(String projectId) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));
        if (project.getFlowType() != FlowType.PRODUCT) {
            throw new BusinessException("仅产品流项目支持任务分配");
        }

        ensureScheduleSlotsForMembers(projectId);
        Map<String, ProjectMemberSchedule> scheduleMap = projectMemberScheduleRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(ProjectMemberSchedule::getUserId, item -> item, (left, right) -> right));

        return projectMemberRepository.findByProjectIdWithUser(projectId).stream()
                .sorted(Comparator.comparingInt((SysProjectMember item) -> {
                    String role = item.getRole() == null ? "" : item.getRole().toUpperCase();
                    if ("ADMIN".equals(role) || "MANAGER".equals(role)) {
                        return 0;
                    }
                    return 1;
                }).thenComparing(item -> item.getUser().getName() == null ? "" : item.getUser().getName()))
                .map(member -> {
                    String userId = member.getUser().getUserId();
                    ProjectMemberSchedule schedule = scheduleMap.get(userId);
                    return ProductTaskAssignmentDTO.builder()
                            .userId(userId)
                            .name(member.getUser().getName() != null ? member.getUser().getName() : member.getUser().getUsername())
                            .role(member.getRole())
                            .taskName(schedule != null ? schedule.getTaskName() : null)
                            .expectedOutput(schedule != null ? schedule.getExpectedOutput() : null)
                            .expectedStartDate(schedule != null ? schedule.getExpectedStartDate() : null)
                            .expectedEndDate(schedule != null ? schedule.getExpectedEndDate() : null)
                            .build();
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> updateTaskAssignments(String projectId, ProductTaskAssignmentUpdateRequest request) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));
        if (project.getFlowType() != FlowType.PRODUCT) {
            throw new BusinessException("仅产品流项目支持任务分配");
        }

        ProductIdeaDetail detail = productIdeaDetailRepository.findByProjectId(projectId)
                .orElse(ProductIdeaDetail.builder().projectId(projectId).build());
        String ideaOwnerUserId = detail.getIdeaOwnerUserId() != null && !detail.getIdeaOwnerUserId().isBlank()
                ? detail.getIdeaOwnerUserId()
                : (project.getManager() != null ? project.getManager().getUserId() : null);
        String managerUserId = project.getManager() == null ? null : project.getManager().getUserId();
        String currentUserId = currentUser.getId();
        boolean canManage = currentUserId.equals(managerUserId) || (ideaOwnerUserId != null && ideaOwnerUserId.equals(currentUserId));
        if (!canManage) {
            throw new PermissionDeniedException("仅 Manager 或发起主理人可进行任务分配");
        }

        ensureScheduleSlotsForMembers(projectId);
        Set<String> memberIds = projectMemberRepository.findByProjectId(projectId).stream()
                .map(member -> member.getUser() == null ? null : member.getUser().getUserId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        int updated = 0;
        List<ProductTaskAssignmentUpdateRequest.AssignmentItem> assignmentItems = request == null || request.getAssignments() == null
                ? List.of()
                : request.getAssignments();
        for (ProductTaskAssignmentUpdateRequest.AssignmentItem item : assignmentItems) {
            if (item == null || item.getUserId() == null || item.getUserId().isBlank()) {
                continue;
            }
            String userId = item.getUserId().trim();
            if (!memberIds.contains(userId)) {
                throw new BusinessException("任务分配对象必须是当前项目成员: " + userId);
            }
            SysProjectMember member = projectMemberRepository.findByProjectIdAndUserUserId(projectId, userId)
                    .orElseThrow(() -> new BusinessException("任务分配对象不存在于项目成员中: " + userId));
            ProjectMemberSchedule schedule = projectMemberScheduleRepository.findByProjectIdAndUserId(projectId, userId)
                    .orElseGet(() -> ProjectMemberSchedule.builder().projectId(projectId).userId(userId).completed(false).managerConfirmed(false).build());
            schedule.setTaskName(blankToNull(item.getTaskName()));
            schedule.setExpectedOutput(blankToNull(item.getExpectedOutput()));
            schedule.setExpectedStartDate(item.getExpectedStartDate());
            schedule.setExpectedEndDate(item.getExpectedEndDate());
            if (schedule.getTaskName() != null && schedule.getExpectedEndDate() == null) {
                throw new BusinessException("任务分配必须填写截止时间: " + userId);
            }
            projectMemberScheduleRepository.save(schedule);

            if (schedule.getTaskName() != null && !schedule.getTaskName().isBlank()) {
                String roleText = roleDisplay(member.getRole());
                String deadlineText = schedule.getExpectedEndDate() == null ? "未设置截止时间" : DEADLINE_FORMATTER.format(schedule.getExpectedEndDate());
                internalMessageService.sendMessage(
                        userId,
                        "TASK_ASSIGNED",
                        "你收到了新的任务分配",
                        "你在项目《" + project.getName() + "》中被以「" + roleText + "」身份分配任务：" + schedule.getTaskName() + "（截止：" + deadlineText + "）",
                        projectId
                );
            }
            updated++;
        }

        return Map.of("success", true, "updated", updated, "projectId", projectId);
    }

    private Set<String> sanitizeIds(java.util.List<String> ids) {
        if (ids == null) return Set.of();
        return ids.stream()
                .map(id -> id == null ? "" : id.trim())
                .filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String sanitizeSelectionId(String userId) {
        return sanitizeUserId(userId);
    }

    private Set<String> sanitizeSelectionIds(java.util.List<String> ids) {
        return sanitizeIds(ids);
    }

    private void ensureScheduleSlotsForMembers(String projectId) {
        projectMemberRepository.findByProjectId(projectId).forEach(member -> {
            if (member.getUser() != null && member.getUser().getUserId() != null && !member.getUser().getUserId().isBlank()) {
                ensureScheduleSlot(projectId, member.getUser().getUserId());
            }
        });
    }

    private void ensureScheduleSlot(String projectId, String userId) {
        projectMemberScheduleRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseGet(() -> projectMemberScheduleRepository.save(ProjectMemberSchedule.builder()
                        .projectId(projectId)
                        .userId(userId)
                        .completed(false)
                        .managerConfirmed(false)
                        .build()));
    }

    private String blankToNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return text.trim();
    }

    private String roleDisplay(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        return switch (normalized) {
            case "ADMIN" -> "主理人";
            case "PROMOTION_IC" -> "推广负责人";
            case "PROMOTION" -> "推广成员";
            case "DEMO_ENG" -> "Demo工程师";
            case "MANAGER" -> "负责人";
            case "DEV" -> "开发";
            case "ALGORITHM" -> "算法";
            case "RESEARCH" -> "研究";
            default -> normalized.isEmpty() ? "成员" : normalized;
        };
    }

    // === Task 4: 阶段 3 - Demo 实施与自动跃迁网关 ===========================

    /**
     * Demo 实施阶段文件上传 + 自动网关
     * POST /api/products/{projectId}/demo/upload
     */
    @Transactional
    public Map<String, Object> uploadDemoAsset(String projectId, MultipartFile file, DemoFileType demoFileType) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        // 仅当前项目的 manager 可上传
        if (project.getManager() == null ||
                !project.getManager().getUserId().equals(currentUser.getId())) {
            throw new PermissionDeniedException("仅项目 Manager/Idea 主理人可以上传 Demo 文件");
        }

        if (project.getFlowType() != FlowType.PRODUCT) {
            throw new BusinessException("非产品流项目无法执行 Demo 上传");
        }

        if (project.getProductStatus() != ProductStatus.DEMO_EXECUTION &&
                project.getProductStatus() != ProductStatus.MEETING_DECISION) {
            throw new BusinessException("仅在 Demo 实施或会议决策阶段允许上传 Demo 相关文件");
        }

        ProductIdeaDetail detail = productIdeaDetailRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("尚未配置 Demo 文件责任人"));
        String expectedUploaderId = getDemoOwnerUserId(detail, demoFileType);
        if (expectedUploaderId == null || expectedUploaderId.isBlank()) {
            throw new BusinessException(formatDemoFileType(demoFileType) + " 尚未指定责任人");
        }
        if (!expectedUploaderId.equals(currentUser.getId())) {
            throw new PermissionDeniedException(formatDemoFileType(demoFileType) + " 必须由指定责任人上传");
        }

        // 物理存储
        String uploadDir = System.getProperty("user.dir") + "/uploads/products/demo/"
                + projectId + "/";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "demo_file_" + System.currentTimeMillis();
        }

        String fileType = "FILE";
        if (originalFilename.contains(".")) {
            fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toUpperCase();
        }

        String savedPath;
        try {
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            Path targetPath = Paths.get(uploadDir + originalFilename);
            file.transferTo(targetPath);
            savedPath = targetPath.toString();
        } catch (IOException e) {
            throw new BusinessException("Demo 文件存储失败: " + e.getMessage());
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
                .assetCategory(demoFileType.name())
                .build();
        projectAssetRepository.save(asset);

        // 网关逻辑：检查是否集齐 4 类文件
        Set<String> requiredCategories = Set.of(
                DemoFileType.ENGINEERING.name(),
                DemoFileType.DEMO_FILE.name(),
                DemoFileType.DESCRIPTION.name(),
                DemoFileType.FEASIBILITY.name()
        );
        long count = projectAssetRepository.countDistinctAssetCategoryByProjectIdAndCategories(
                projectId, requiredCategories);

        boolean autoTransition = false;
        if (count >= requiredCategories.size()
                && project.getProductStatus() == ProductStatus.DEMO_EXECUTION) {
            project.setProductStatus(ProductStatus.MEETING_DECISION);
            projectRepository.save(project);
            autoTransition = true;
            log.info("[ProductFlow] 项目 {} 已集齐 4 类 Demo 文件，自动跃迁至 MEETING_DECISION 阶段", projectId);
        }

        return Map.of(
                "success", true,
                "fileName", originalFilename,
                "fileType", fileType,
                "assetCategory", demoFileType.name(),
                "autoTransition", autoTransition,
                "currentStatus", project.getProductStatus().name()
        );
    }

    private String getDemoOwnerUserId(ProductIdeaDetail detail, DemoFileType demoFileType) {
        return switch (demoFileType) {
            case ENGINEERING -> sanitizeUserId(detail.getDemoEngineeringOwnerUserId());
            case DEMO_FILE -> sanitizeUserId(detail.getDemoFileOwnerUserId());
            case DESCRIPTION -> sanitizeUserId(detail.getDemoDescriptionOwnerUserId());
            case FEASIBILITY -> sanitizeUserId(detail.getDemoFeasibilityOwnerUserId());
        };
    }

    private String formatDemoFileType(DemoFileType demoFileType) {
        return switch (demoFileType) {
            case ENGINEERING -> "工程文件";
            case DEMO_FILE -> "Demo 演示文件";
            case DESCRIPTION -> "描述文档";
            case FEASIBILITY -> "可行性验证材料";
        };
    }

    private String sanitizeUserId(String userId) {
        if (userId == null) {
            return null;
        }
        String trimmed = userId.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    // === Task 1: 阶段 4 - 虚拟会议决策 (Meeting Decision) ===================

    /**
     * 虚拟会议阶段提交会议纪要与决策结果。
     * 路径：POST /api/products/{projectId}/meeting-decision
     *
     * @param projectId       产品流项目 ID（严格隔离维度）
     * @param meetingMinutes  会议纪要文件
     * @param decision        决策结果：OK / HOLD
     */
    @Transactional
    public Map<String, Object> submitMeetingDecision(String projectId,
                                                     MultipartFile meetingMinutes,
                                                     String decision,
                                                     java.util.List<String> participantUserIds) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        // 1. 基于 projectId 精确加载项目，避免误操作其他项目
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        if (participantUserIds == null || participantUserIds.isEmpty()) {
            throw new BusinessException("参会成员不能为空，且必须关联用户");
        }
        Set<String> participants = participantUserIds.stream()
                .map(id -> id == null ? "" : id.trim())
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());
        if (participants.isEmpty()) {
            throw new BusinessException("参会成员不能为空，且必须关联用户");
        }
        Set<String> memberIds = projectMemberRepository.findByProjectId(projectId).stream()
                .map(member -> member.getUser().getUserId())
                .collect(Collectors.toSet());
        if (!memberIds.containsAll(participants)) {
            throw new BusinessException("参会成员必须全部来自当前项目成员");
        }

        // 2. 仅项目 manager（Idea 主理人）可提交会议决策
        if (project.getManager() == null ||
                !project.getManager().getUserId().equals(currentUser.getId())) {
            throw new PermissionDeniedException("仅项目 Manager/Idea 主理人可以提交会议决策");
        }

        // 3. 必须是产品流 + 当前处于 MEETING_DECISION 阶段
        if (project.getFlowType() != FlowType.PRODUCT) {
            throw new BusinessException("非产品流项目无法提交会议决策");
        }
        if (project.getProductStatus() != ProductStatus.MEETING_DECISION) {
            throw new BusinessException("只有在 MEETING_DECISION 阶段才能提交会议决策");
        }

        // 4. 归一化决策入参（大小写不敏感）
        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase();
        if (!"OK".equals(normalizedDecision) && !"HOLD".equals(normalizedDecision)) {
            throw new BusinessException("决策参数 decision 仅允许为 OK 或 HOLD");
        }

        // 5. 将会议纪要以 ProjectAsset 形式落盘，保持与其他文件统一管理
        String uploadDir = System.getProperty("user.dir") + "/uploads/products/meeting/" + projectId + "/";
        String originalFilename = meetingMinutes.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "meeting_minutes_" + System.currentTimeMillis();
        }

        String fileType = "FILE";
        if (originalFilename.contains(".")) {
            fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toUpperCase();
        }

        String savedPath;
        try {
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            Path targetPath = Paths.get(uploadDir + originalFilename);
            meetingMinutes.transferTo(targetPath);
            savedPath = targetPath.toString();
        } catch (IOException e) {
            throw new BusinessException("会议纪要文件存储失败: " + e.getMessage());
        }

        User uploader = userRepository.findById(currentUser.getId()).orElse(null);
        ProjectAsset asset = ProjectAsset.builder()
                .project(project)
                .fileName(originalFilename)
                .fileType(fileType)
                .filePath(savedPath)
                .fileData(readFileBytes(meetingMinutes))
                .contentType(meetingMinutes.getContentType())
                .fileSize(meetingMinutes.getSize())
                .uploaderName(uploader != null ? uploader.getName() : "Unknown")
                .uploadedAt(Instant.now())
                // 使用专用分类标记为会议纪要
                .assetCategory("MEETING_MINUTES")
                .build();
        projectAssetRepository.save(asset);

        ProductIdeaDetail detail = productIdeaDetailRepository.findByProjectId(projectId)
                .orElse(ProductIdeaDetail.builder().projectId(projectId).ideaOwnerUserId(project.getManager().getUserId()).build());
        detail.setMeetingParticipantUserIds(String.join(",", participants));
        productIdeaDetailRepository.save(detail);

        // 6. 决策驱动状态分支跃迁（无任何 OA / 审批流）
        if ("OK".equals(normalizedDecision)) {
            project.setProductStatus(ProductStatus.TESTING);
        } else {
            project.setProductStatus(ProductStatus.SHELVED);
            project.setEndDate(Instant.now());
        }
        projectRepository.save(project);

        log.info("[ProductFlow] 项目 {} 会议决策提交完成，decision={}, 新状态={}",
                projectId, normalizedDecision, project.getProductStatus());

        return Map.of(
                "success", true,
                "decision", normalizedDecision,
                "currentStatus", project.getProductStatus().name()
        );
    }

    // === Task 2: 阶段 5 - 测试与正式上线网关 (Testing & Launch) =============

    /**
     * 测试阶段反馈与正式上线网关。
     * 路径：POST /api/products/{projectId}/testing-feedback
     *
     * 由推广负责人 (PROMOTION_IC) 决定是否通过测试，并在通过时触发正式项目的生成。
     */
    @Transactional
    public Map<String, Object> submitTestingFeedback(String projectId,
                                                     String testFeedback,
                                                     boolean isPassed) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        // 1. 基于 projectId 加载项目，保证操作隔离
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        if (project.getFlowType() != FlowType.PRODUCT) {
            throw new BusinessException("非产品流项目无法提交测试反馈");
        }
        if (project.getProductStatus() != ProductStatus.TESTING) {
            throw new BusinessException("只有在 TESTING 阶段才能提交测试反馈");
        }

        // 2. 权限路由：当前登录用户必须在 SysProjectMember 中拥有 PROMOTION_IC 角色
        SysProjectMember member = projectMemberRepository
                .findByProjectIdAndUserUserId(projectId, currentUser.getId())
                .orElseThrow(() -> new PermissionDeniedException("您不是该项目的成员，无法提交测试反馈"));

        if (!"PROMOTION_IC".equals(member.getRole())) {
            throw new PermissionDeniedException("仅 PROMOTION_IC (推广负责人) 可以提交测试反馈");
        }

        long testerCount = projectChatMessageRepository.countDistinctSenderUserIdByProjectIdAndStageTag(projectId, "TESTING");
        if (isPassed && testerCount < MIN_TESTERS) {
            throw new BusinessException("内测参与人数不足，至少需要 " + MIN_TESTERS + " 人");
        }

        // 3. 将测试反馈保存到 Sidecar 扩展表 ProductIdeaDetail 中
        Optional<ProductIdeaDetail> optionalDetail = productIdeaDetailRepository.findByProjectId(projectId);
        ProductIdeaDetail detail = optionalDetail.orElse(
                ProductIdeaDetail.builder().projectId(projectId).build()
        );
        detail.setTestFeedback(testFeedback);
        productIdeaDetailRepository.save(detail);

        // 4. 根据 isPassed 决定状态分支
        SysProject spawned = null;
        if (!isPassed) {
            project.setProductStatus(ProductStatus.SHELVED);
            projectRepository.save(project);
            log.info("[ProductFlow] 项目 {} 测试未通过，状态跃迁至 SHELVED", projectId);
        } else {
            project.setProductStatus(ProductStatus.LAUNCHED);
            projectRepository.save(project);
            log.info("[ProductFlow] 项目 {} 测试通过，状态跃迁至 LAUNCHED，开始生成正式项目", projectId);

            // 通过测试则触发终极跃迁：生成正式项目流数据
            spawned = spawnFormalProject(project);
            applyPromotionExpense(project);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("isPassed", isPassed);
        response.put("currentStatus", project.getProductStatus().name());
        response.put("testerCount", testerCount);
        response.put("spawnedProjectId", spawned != null ? spawned.getProjectId() : null);
        return response;
    }

    private void applyPromotionExpense(SysProject productProject) {
        List<SysProjectMember> promotionMembers = projectMemberRepository.findByProjectIdWithUser(productProject.getProjectId()).stream()
                .filter(member -> "PROMOTION".equals(member.getRole()) || "PROMOTION_IC".equals(member.getRole()))
                .toList();
        if (promotionMembers.isEmpty()) {
            return;
        }

        BigDecimal baseAmount = productProject.getBudget() != null && productProject.getBudget().compareTo(BigDecimal.ZERO) > 0
                ? productProject.getBudget()
                : productProject.getCost();
        BigDecimal totalExpense = FinanceAmounts.scale(FinanceAmounts.scale(baseAmount).multiply(PROMOTION_EXPENSE_RATE));
        if (totalExpense.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < promotionMembers.size(); i++) {
            SysProjectMember member = promotionMembers.get(i);
            User promoter = member.getUser();
            BigDecimal expense = i == promotionMembers.size() - 1
                    ? FinanceAmounts.scale(totalExpense.subtract(allocated))
                    : FinanceAmounts.scale(totalExpense.divide(BigDecimal.valueOf(promotionMembers.size()), 2, java.math.RoundingMode.HALF_UP));
            allocated = FinanceAmounts.add(allocated, expense);

            FinanceWalletAccount wallet = financeReferenceService.getOrCreateWallet(promoter.getUserId());
            BigDecimal nextBalance = FinanceAmounts.subtract(wallet.getBalance(), expense);
            wallet.setBalance(nextBalance);
            wallet.setTotalPromotionExpense(FinanceAmounts.add(wallet.getTotalPromotionExpense(), expense));
            walletAccountRepository.save(wallet);

            walletTransactionRepository.save(FinanceWalletTransaction.builder()
                    .wallet(wallet)
                    .transactionType(FinanceWalletTransactionType.PROMOTION_EXPENSE)
                    .cashFlowDirection(FinanceCashFlowDirection.OUT)
                    .amount(expense)
                    .balanceAfter(nextBalance)
                    .project(productProject)
                    .sourceTable("sys_project")
                    .sourceId(null)
                    .remark("Promotion expense for product launch")
                    .build());
        }
    }

    private void validateIdeaRequest(ProductIdeaRequest request) {
        if (request == null) {
            throw new BusinessException("请求体不能为空");
        }
        if (isBlank(request.getName())) {
            throw new BusinessException("产品名称不能为空");
        }
        if (request.getExpectedBudget() == null) {
            throw new BusinessException("预计项目投入不能为空");
        }
        if (request.getProjectType() == null) {
            throw new BusinessException("产品方向不能为空");
        }
        if (!Set.of(
                ProjectType.INDUSTRIAL,
                ProjectType.MILITARY,
                ProjectType.MEDICAL,
                ProjectType.AI_FOR_SCIENCE,
                ProjectType.SWARM_INTEL
        ).contains(request.getProjectType())) {
            throw new BusinessException("产品方向无效，仅支持：工业/军工/医药/AI for Science/群体智能");
        }
        if (isBlank(request.getTargetUsers())) {
            throw new BusinessException("目标用户群不能为空");
        }
        if (isBlank(request.getCoreFeatures())) {
            throw new BusinessException("主打功能点不能为空");
        }
        if (isBlank(request.getUseCase())) {
            throw new BusinessException("用途不能为空");
        }
        if (isBlank(request.getProblemStatement())) {
            throw new BusinessException("针对的问题不能为空");
        }
        if (isBlank(request.getTechStackDesc())) {
            throw new BusinessException("可能涉及的技术栈和深度不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // === Task 3: 终极跃迁 - Idea 转化为正式项目 (Data Spawning) ==============

    /**
     * 将已经 LAUNCHED 的产品流项目转化为正式的项目流 (FlowType.PROJECT)。
     *
     * 该方法严格做到“新建数据，不破坏原数据”：
     * 1. 读取原产品流项目的基础信息（名称、描述、manager 等）
     * 2. 创建新的 SysProject，flowType = PROJECT，状态直接落在 IMPLEMENTING（实施阶段）
     * 3. 将原 manager 平滑继承到新项目的 SysProjectMember 中，并赋予 MANAGER 角色
     * 4. 原产品流项目仅保持为 LAUNCHED 状态，不做任何反向修改
     */
    private SysProject spawnFormalProject(SysProject sourceProductProject) {
        // 安全保护：只允许在 LAUNCHED 状态下触发生成
        if (sourceProductProject.getProductStatus() != ProductStatus.LAUNCHED) {
            throw new BusinessException("只有 LAUNCHED 状态的产品流项目才允许生成正式项目");
        }

        User manager = sourceProductProject.getManager();
        if (manager == null) {
            throw new BusinessException("源产品项目缺少 manager，无法生成正式项目");
        }

        // 1. 基于原项目信息构造新的项目流 SysProject（不复用 ID，彻底新生）
        String newProjectId = UUID.randomUUID().toString();
        SysProject formalProject = SysProject.builder()
                .projectId(newProjectId)
                .name(sourceProductProject.getName())
                .description(sourceProductProject.getDescription())
                .manager(manager)
                // 将 flowType 切换为 PROJECT，表示进入对外交付的项目流
                .flowType(FlowType.PROJECT)
                .projectType(sourceProductProject.getProjectType() != null ? sourceProductProject.getProjectType() : ProjectType.BUSINESS)
                // 直接进入实施阶段，对应 EXECUTION
                .projectStatus(ProjectStatus.IMPLEMENTING)
                // 产品状态在项目流中不再重要，这里保持默认 IDEA 即可
                .productStatus(ProductStatus.IDEA)
                // 可以沿用预算等信息，也可以重新评估；此处简单继承预算
                .budget(sourceProductProject.getBudget())
                .cost(sourceProductProject.getCost())
                .techStack(sourceProductProject.getTechStack())
                .repoUrl(sourceProductProject.getRepoUrl())
                .deployUrl(sourceProductProject.getDeployUrl())
                .build();

        SysProject savedFormal = projectRepository.save(formalProject);

        // 2. 角色平滑继承：将原 manager 写入新项目成员表，赋予 MANAGER 角色
        if (!projectMemberRepository.existsByProjectIdAndUserUserId(newProjectId, manager.getUserId())) {
            SysProjectMember managerMember = SysProjectMember.builder()
                    .projectId(newProjectId)
                    .user(manager)
                    .role("MANAGER")
                    .weight(0)
                    .build();
            projectMemberRepository.save(managerMember);
            workflowMemberRoleSyncService.sync(FlowType.PROJECT.name(), newProjectId, manager.getUserId(), managerMember.getRole());
        }

        log.info("[ProductFlow] 产品项目 {} 已生成正式项目 {} ，manager={}",
                sourceProductProject.getProjectId(), newProjectId, manager.getUserId());

        return savedFormal;
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("文件内容读取失败: " + e.getMessage());
        }
    }
}
