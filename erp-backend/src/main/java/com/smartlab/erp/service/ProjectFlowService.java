package com.smartlab.erp.service;

import com.smartlab.erp.dto.ExecutionOverviewResponseDTO;
import com.smartlab.erp.dto.ExecutionPlanRequestDTO;
import com.smartlab.erp.dto.ProjectTaskAssignmentDTO;
import com.smartlab.erp.dto.ProjectTaskAssignmentUpdateRequest;
import com.smartlab.erp.dto.ProductMemberUpdateRequest;
import com.smartlab.erp.dto.ScheduleDTO;
import com.smartlab.erp.entity.*;
import com.smartlab.erp.enums.BusinessRoleEnum;
import com.smartlab.erp.enums.FolderTypeEnum;
import com.smartlab.erp.event.ProjectSettlementEvent;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.repository.*;
import com.smartlab.erp.util.AuthUtils;
import com.smartlab.erp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 项目流核心业务服务
 *
 * 负责处理实施阶段(EXECUTION)和结算归档阶段(SETTLEMENT)的全部业务逻辑。
 *
 * 核心设计原则：
 * 1. 所有操作都通过 projectId 进行物理隔离，绝不影响其他项目
 * 2. 不侵入现有的 SysProject 核心实体，新数据存入扩展表
 * 3. 无审批流，全部依靠角色"动作触发"进行状态跃迁
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectFlowService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final SysProjectRepository projectRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final ProjectExecutionPlanRepository executionPlanRepository;
    private final ProjectService projectService;
    private final ProjectMemberParticipationService projectMemberParticipationService;
    private final ProjectMemberScheduleRepository memberScheduleRepository;
    private final ExecutionFileRepository executionFileRepository;
    private final ExecutionArchiveFolderRepository executionArchiveFolderRepository;
    private final ProjectSubtaskRepository projectSubtaskRepository;
    private final UserRepository userRepository;
    private final OcrService ocrService;
    private final ApplicationEventPublisher eventPublisher;
    private final InternalMessageService internalMessageService;

    @Value("${auth.admin-usernames:Zhangqi,guojianwen,jiaomiao}")
    private String adminUsernamesConfig;

    private Set<String> getAdminUsernames() {
        return Set.of(adminUsernamesConfig.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean isAdminUser(String role, String username) {
        if (role != null && "ADMIN".equalsIgnoreCase(role.trim())) {
            return true;
        }
        return username != null && getAdminUsernames().contains(username.trim());
    }

    // ====================================================================
    // Task 1: 实施阶段 - Manager 目标管控与进度设定
    // POST /api/projects/{projectId}/execution/plan
    // ====================================================================

    /**
     * 设定实施计划
     * 
     * 权限：仅当前项目的 MANAGER 可以调用
     * 前置：项目状态必须为 IMPLEMENTING (对应 EXECUTION 阶段)
     * 
     * @param projectId 项目ID (物理隔离键)
     * @param request   实施计划请求体
     * @return 操作结果
     */
    @Transactional
    public Map<String, Object> setExecutionPlan(String projectId, ExecutionPlanRequestDTO request) {
        // ===== 1. 获取当前登录用户 =====
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        String currentUserId = currentUser.getId();

        // ===== 2. 加载项目并校验状态 =====
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        // 校验项目当前状态必须为 IMPLEMENTING (对应 EXECUTION 阶段)
        if (project.getProjectStatus() != ProjectStatus.IMPLEMENTING) {
            throw new BusinessException("项目当前状态不是实施阶段(EXECUTION)，无法设定计划。当前状态: "
                    + project.getProjectStatus().getStageName());
        }

        // ===== 3. 权限校验：仅 MANAGER 可调用 =====
        // 检查当前用户是否是该项目的 MANAGER
        checkIsProjectManager(projectId, currentUserId);

        // ===== 4. 持久化实施计划（扩展表，不侵入 SysProject） =====
        ProjectExecutionPlan plan = executionPlanRepository.findByProjectId(projectId)
                .orElse(new ProjectExecutionPlan());

        if (request.getGoalDescription() == null || request.getGoalDescription().isBlank()) {
            throw new BusinessException("实施目标不能为空");
        }
        if (request.getDifficultyLevel() == null || request.getDifficultyLevel().isBlank()) {
            throw new BusinessException("请先选择项目难度分级");
        }
        if (request.getTechStackDescription() == null || request.getTechStackDescription().isBlank()) {
            throw new BusinessException("请描述可能涉及的技术栈和深度");
        }

        plan.setProjectId(projectId);
        plan.setDifficultyLevel(request.getDifficultyLevel());
        plan.setProjectTier(request.getProjectTier());
        plan.setGoalDescription(request.getGoalDescription());
        plan.setTechStackDescription(request.getTechStackDescription());
        plan.setCreatedBy(currentUserId);
        executionPlanRepository.save(plan);
        project.setProjectTier(request.getProjectTier());
        projectRepository.save(project);

        // ===== 5. 持久化成员排期（扩展表） =====
        if (request.getMemberSchedules() != null && !request.getMemberSchedules().isEmpty()) {
            for (ScheduleDTO scheduleDTO : request.getMemberSchedules()) {
                if (scheduleDTO.getTaskName() == null || scheduleDTO.getTaskName().isBlank()) {
                    throw new BusinessException("成员任务名称不能为空");
                }
                if (scheduleDTO.getExpectedOutput() == null || scheduleDTO.getExpectedOutput().isBlank()) {
                    throw new BusinessException("成员任务产出不能为空");
                }
                Instant expectedStart = parseInstant(scheduleDTO.getExpectedStartDate());
                Instant expectedEnd = parseInstant(scheduleDTO.getExpectedEndDate());
                if (expectedStart == null || expectedEnd == null) {
                    throw new BusinessException("成员任务开始/结束时间不能为空，且需精确到分钟");
                }
                if (!expectedEnd.isAfter(expectedStart)) {
                    throw new BusinessException("成员任务结束时间必须晚于开始时间");
                }

                // 校验该成员确实是项目成员
                boolean isMember = projectMemberRepository
                        .existsByProjectIdAndUserUserId(projectId, scheduleDTO.getUserId());
                if (!isMember) {
                    throw new BusinessException("用户 " + scheduleDTO.getUserId() + " 不是该项目的成员，无法设定排期");
                }

                // 查找或创建排期记录
                ProjectMemberSchedule schedule = memberScheduleRepository
                        .findByProjectIdAndUserId(projectId, scheduleDTO.getUserId())
                        .orElse(new ProjectMemberSchedule());

                schedule.setProjectId(projectId);
                schedule.setUserId(scheduleDTO.getUserId());
                schedule.setExpectedStartDate(expectedStart);
                schedule.setExpectedEndDate(expectedEnd);
                schedule.setTaskName(scheduleDTO.getTaskName() == null ? null : scheduleDTO.getTaskName().trim());
                schedule.setExpectedOutput(scheduleDTO.getExpectedOutput() == null ? null : scheduleDTO.getExpectedOutput().trim());
                memberScheduleRepository.save(schedule);
                internalMessageService.sendMessage(scheduleDTO.getUserId(), "DDL_REMINDER", "你收到新的项目排期", "项目「" + project.getName() + "」排期已更新，DDL: " + formatDateTime(expectedEnd), projectId);
            }
        }

        log.info("[实施计划] 项目 {} 的实施计划已设定，操作人: {}", projectId, currentUserId);

        return Map.of(
                "success", true,
                "message", "实施计划设定成功",
                "projectId", projectId
        );
    }

    // ====================================================================
    // Task 2: 实施阶段 - 强制双盲文件隔离上传
    // POST /api/projects/{projectId}/execution/upload
    // ====================================================================

    /**
     * 双盲隔离文件上传
     * 
     * 权限路由规则：
     * - folderType = A_MANAGER_ARCHIVE → 仅 MANAGER 可上传
     * - folderType = B_ENGINEER_WORK   → 仅项目成员（非 MANAGER）可上传
     * 
     * @param projectId  项目ID (物理隔离键)
     * @param file       上传的文件
     * @param folderType 目标隔离区类型
     * @return 上传结果
     */
    @Transactional
    public Map<String, Object> uploadExecutionFile(String projectId, MultipartFile file, FolderTypeEnum folderType, String secondaryCategory) {
        // ===== 1. 获取当前登录用户 =====
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        String currentUserId = currentUser.getId();

        // ===== 2. 加载项目并校验状态 =====
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        if (!canUseExecutionWorkspace(project)) {
            throw new BusinessException("项目当前阶段未开放 A/B 文件模式，无法上传文件");
        }

        // ===== 3. 校验当前用户是否是该项目成员 =====
        boolean isManager = isProjectManager(projectId, currentUserId);
        if (!isManager) {
            projectMemberRepository.findByProjectIdAndUserUserId(projectId, currentUserId)
                    .orElseThrow(() -> new PermissionDeniedException("权限不足：您不是该项目的成员"));
        }

        // ===== 4. 强制权限路由 (双盲隔离核心) =====
        if (folderType == FolderTypeEnum.A_MANAGER_ARCHIVE) {
            // A 区：仅 MANAGER 可上传
            // 判断条件：成员角色包含 MANAGER，或者是项目的 manager
            if (!isManager) {
                throw new PermissionDeniedException(
                        "越权操作：只有项目 Manager 才能上传至 Manager 归档区");
            }
            secondaryCategory = normalizeArchiveFolderPath(secondaryCategory);
            ensureArchiveFolderHierarchy(projectId, secondaryCategory, currentUserId);
        } else if (folderType == FolderTypeEnum.B_ENGINEER_WORK) {
            // B 区：仅非 Manager 成员可上传自己的实施成果
            if (isManager) {
                throw new PermissionDeniedException("Manager 请将文件上传至文件夹 A（管理归档区）");
            }
            secondaryCategory = normalizeArchiveFolderPath(secondaryCategory);
            log.info("[双盲上传] 工程师 {} 上传至工程师作业区", currentUserId);
        }

        // ===== 5. 物理文件存储 =====
        // 按 projectId/folderType 进行物理目录隔离
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) originalFilename = "unknown_file";

        String fileType = "FILE";
        if (originalFilename.contains(".")) {
            fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toUpperCase();
        }

        String savedPath;
        try {
            String folderPath = secondaryCategory;
            Path targetPath = resolveExecutionFilePath(projectId, folderType, folderPath, originalFilename);
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
            savedPath = targetPath.toString();
        } catch (IOException e) {
            throw new BusinessException("文件存储失败: " + e.getMessage());
        }

        // ===== 6. 记录文件元数据（扩展表，绑定 uploaderUserId） =====
        User uploader = userRepository.findById(currentUserId).orElse(null);
        ExecutionFile executionFile = ExecutionFile.builder()
                .projectId(projectId)
                .folderType(folderType)
                .uploaderUserId(currentUserId)
                .uploaderName(uploader != null ? uploader.getName() : "Unknown")
                .fileName(originalFilename)
                .fileType(fileType)
                .secondaryCategory(normalizeSecondaryCategory(secondaryCategory))
                .filePath(savedPath)
                .fileSize(file.getSize())
                .uploadedAt(Instant.now())
                .build();
        executionFileRepository.save(executionFile);

        // ===== 7. 可选：标记工程师个人节点结业 =====
        if (folderType == FolderTypeEnum.B_ENGINEER_WORK) {
            memberScheduleRepository.findByProjectIdAndUserId(projectId, currentUserId)
                    .ifPresent(schedule -> {
                        if (!Boolean.TRUE.equals(schedule.getCompleted())) {
                            schedule.setCompleted(true);
                            schedule.setActualEndDate(Instant.now());
                            memberScheduleRepository.save(schedule);
                            log.info("[双盲上传] 工程师 {} 的个人节点已标记结业", currentUserId);
                        }
                    });
        }

        log.info("[双盲上传] 项目 {} - {} 区 - 文件 {} 上传成功",
                projectId, folderType.name(), originalFilename);

        return Map.of(
                "success", true,
                "message", "文件上传成功",
                "fileName", originalFilename,
                "folderType", folderType.name()
        );
    }

    @Transactional(readOnly = true)
    public ExecutionOverviewResponseDTO getExecutionOverview(String projectId) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        String currentUserId = currentUser.getId();
        boolean isAdmin = isAdminUser(currentUser.getRole(), currentUser.getUsername());
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        if (!isAdmin && !isProjectManager(projectId, currentUserId)) {
            projectMemberRepository.findByProjectIdAndUserUserId(projectId, currentUserId)
                    .orElseThrow(() -> new PermissionDeniedException("权限不足：您不是该项目的成员"));
        }

        boolean isManager = isAdmin || isProjectManager(projectId, currentUserId);
        ProjectExecutionPlan plan = executionPlanRepository.findByProjectId(projectId).orElse(null);
        List<SysProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        Map<String, ProjectMemberSchedule> schedulesByUserId = memberScheduleRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(ProjectMemberSchedule::getUserId, schedule -> schedule, (left, right) -> right));

        List<ExecutionOverviewResponseDTO.MemberScheduleInfo> scheduleInfos = members.stream()
                .filter(member -> !isBusinessTaskRole(member.getRole()))
                .map(member -> buildScheduleInfo(member, schedulesByUserId.get(member.getUser().getUserId())))
                .collect(Collectors.toList());

        List<ExecutionFile> managerFiles = isManager
                ? executionFileRepository.findByProjectIdAndFolderTypeOrderByUploadedAtDesc(projectId, FolderTypeEnum.A_MANAGER_ARCHIVE)
                : List.of();
        boolean canUploadInCurrentStage = canUseExecutionWorkspace(project);
        List<ExecutionFile> engineerFiles = isManager
                ? executionFileRepository.findByProjectIdAndFolderTypeOrderByUploadedAtDesc(projectId, FolderTypeEnum.B_ENGINEER_WORK)
                : executionFileRepository.findByProjectIdAndUploaderUserIdOrderByUploadedAtDesc(projectId, currentUserId);

        return ExecutionOverviewResponseDTO.builder()
                .canManage(isManager)
                .canUploadManagerFiles(isManager && canUploadInCurrentStage)
                .canUploadEngineerFiles(!isManager && canUploadInCurrentStage)
                .plan(plan == null ? null : ExecutionOverviewResponseDTO.PlanInfo.builder()
                        .difficultyLevel(plan.getDifficultyLevel())
                        .projectTier(plan.getProjectTier())
                        .goalDescription(plan.getGoalDescription())
                        .techStackDescription(plan.getTechStackDescription())
                        .build())
                .schedules(scheduleInfos)
                .managerArchiveFiles(managerFiles.stream().map(file -> toExecutionFileInfo(file, currentUserId, isManager)).collect(Collectors.toList()))
                .managerArchiveFolders(isManager ? buildManagerArchiveFolders(projectId, managerFiles) : List.of())
                .engineerWorkspaceFiles(engineerFiles.stream().map(file -> toExecutionFileInfo(file, currentUserId, isManager)).collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public Map<String, Object> updateExecutionMembers(String projectId, ProductMemberUpdateRequest request) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        String currentUserId = currentUser.getId();

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        if (project.getFlowType() != FlowType.PROJECT) {
            throw new BusinessException("仅项目流支持实施阶段成员动态调整");
        }
        if (project.getProjectStatus() == ProjectStatus.COMPLETED) {
            throw new BusinessException("归档阶段不允许再调整成员");
        }

        checkIsProjectManager(projectId, currentUserId);

        Set<String> addUserIds = sanitizeIds(request != null ? request.getAddUserIds() : null);
        Set<String> removeUserIds = sanitizeIds(request != null ? request.getRemoveUserIds() : null);

        String managerUserId = project.getManager() == null ? null : project.getManager().getUserId();
        if (managerUserId != null && removeUserIds.contains(managerUserId)) {
            throw new BusinessException("项目经理不能被移除，请先重新指定经理");
        }

        int added = 0;
        int removed = 0;

        for (String userId : addUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在: " + userId));
            if (!projectMemberRepository.existsByProjectIdAndUserUserId(projectId, userId)) {
                projectMemberRepository.save(SysProjectMember.builder()
                        .projectId(projectId)
                        .user(user)
                        .role("MEMBER")
                        .weight(0)
                        .managerWeight(0)
                        .build());
                projectMemberRepository.findByProjectIdAndUserUserId(projectId, userId)
                        .ifPresent(projectMemberParticipationService::recordJoin);
                memberScheduleRepository.findByProjectIdAndUserId(projectId, userId)
                        .orElseGet(() -> memberScheduleRepository.save(ProjectMemberSchedule.builder()
                                .projectId(projectId)
                                .userId(userId)
                                .completed(false)
                                .managerConfirmed(false)
                                .build()));
                if (!userId.equals(currentUserId)) {
                    internalMessageService.sendMessage(
                            userId,
                            "PROJECT_JOINED",
                            "你已加入项目",
                            "你被选中加入项目《" + project.getName() + "》",
                            projectId
                    );
                }
                added++;
            }
            projectMemberRepository.findByProjectIdAndUserUserId(projectId, userId)
                    .ifPresent(member -> projectMemberParticipationService.recordJoin(projectId, member.getUser(), member.getJoinedAt()));
        }

        for (String userId : removeUserIds) {
            projectMemberParticipationService.recordLeave(projectId, userId, Instant.now());
            long delta = projectMemberRepository.deleteByProjectIdAndUserUserId(projectId, userId);
            if (delta > 0) {
                memberScheduleRepository.deleteByProjectIdAndUserId(projectId, userId);
                removed += delta;
            }
        }

        String updatedManagerUserId = project.getManager() == null ? null : project.getManager().getUserId();
        List<SysProjectMember> currentMembers = projectMemberRepository.findByProjectId(projectId);
        boolean hasExecutionMembers = currentMembers.stream()
                .anyMatch(member -> {
                    if (member.getUser() == null || member.getUser().getUserId() == null) {
                        return false;
                    }
                    String uid = member.getUser().getUserId();
                    if (updatedManagerUserId != null && updatedManagerUserId.equals(uid)) {
                        return false;
                    }
                    return projectService.isExecutionMemberRole(member.getRole());
                });
        if (!hasExecutionMembers) {
            throw new BusinessException("至少保留1名执行成员，不能将项目成员清空后继续实施");
        }

        boolean shouldUpdateResponsibility = request != null
                && (request.getManagerWeight() != null
                || request.getManagerExecutionWeight() != null
                || (request.getResponsibilityMembers() != null && !request.getResponsibilityMembers().isEmpty()));
        if (shouldUpdateResponsibility) {
            List<ProjectService.ProjectMemberRatioInput> ratioInputs = request.getResponsibilityMembers() == null
                    ? List.of()
                    : request.getResponsibilityMembers().stream()
                    .filter(item -> item != null && item.getUserId() != null && !item.getUserId().trim().isBlank())
                    .map(item -> new ProjectService.ProjectMemberRatioInput(
                            item.getUserId().trim(),
                            projectService.normalizeMemberRole(item.getRole()),
                            item.getWeight()))
                    .toList();
            projectService.applyProjectResponsibilityAllocation(project, updatedManagerUserId, request.getManagerWeight(), request.getManagerExecutionWeight(), ratioInputs);
        }

        return Map.of(
                "success", true,
                "added", added,
                "removed", removed,
                "projectId", projectId,
                "status", project.getProjectStatus().name()
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectTaskAssignmentDTO> getProjectTaskAssignments(String projectId) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));
        if (project.getFlowType() != FlowType.PROJECT) {
            throw new BusinessException("仅项目流支持任务分配");
        }

        String currentUserId = currentUser.getId();
        boolean isAdmin = isAdminUser(currentUser.getRole(), currentUser.getUsername());
        boolean isMember = isAdmin
                || projectMemberRepository.existsByProjectIdAndUserUserId(projectId, currentUserId)
                || projectRepository.isManager(projectId, currentUserId);
        if (!isMember) {
            throw new PermissionDeniedException("仅项目成员可查看任务分配");
        }

        ensureScheduleSlotsForMembers(projectId);
        Map<String, ProjectMemberSchedule> scheduleByUserId = memberScheduleRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(ProjectMemberSchedule::getUserId, schedule -> schedule, (left, right) -> right));

        return projectMemberRepository.findByProjectIdWithUser(projectId).stream()
                .filter(member -> !isBusinessTaskRole(member.getRole()))
                .map(member -> {
                    String userId = member.getUser().getUserId();
                    ProjectMemberSchedule schedule = scheduleByUserId.get(userId);
                    return ProjectTaskAssignmentDTO.builder()
                            .userId(userId)
                            .name(member.getUser().getName() != null ? member.getUser().getName() : member.getUser().getUsername())
                            .role(member.getRole())
                            .taskName(schedule == null ? null : schedule.getTaskName())
                            .expectedOutput(schedule == null ? null : schedule.getExpectedOutput())
                            .expectedEndDate(schedule == null ? null : schedule.getExpectedEndDate())
                            .build();
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> updateProjectTaskAssignments(String projectId, ProjectTaskAssignmentUpdateRequest request) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));
        if (project.getFlowType() != FlowType.PROJECT) {
            throw new BusinessException("仅项目流支持任务分配");
        }

        checkIsProjectManager(projectId, currentUser.getId());
        ensureScheduleSlotsForMembers(projectId);

        Set<String> memberIds = projectMemberRepository.findByProjectId(projectId).stream()
                .filter(member -> !isBusinessTaskRole(member.getRole()))
                .map(member -> member.getUser() == null ? null : member.getUser().getUserId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        List<ProjectTaskAssignmentUpdateRequest.AssignmentItem> items = request == null || request.getAssignments() == null
                ? List.of()
                : request.getAssignments();

        int updated = 0;
        for (ProjectTaskAssignmentUpdateRequest.AssignmentItem item : items) {
            if (item == null || item.getUserId() == null || item.getUserId().isBlank()) {
                continue;
            }
            String userId = item.getUserId().trim();
            if (!memberIds.contains(userId)) {
                throw new BusinessException("任务分配对象必须是当前项目成员: " + userId);
            }
            ProjectMemberSchedule schedule = memberScheduleRepository.findByProjectIdAndUserId(projectId, userId)
                    .orElseGet(() -> ProjectMemberSchedule.builder()
                            .projectId(projectId)
                            .userId(userId)
                            .completed(false)
                            .managerConfirmed(false)
                            .build());
            String taskName = item.getTaskName() == null ? null : item.getTaskName().trim();
            schedule.setTaskName(taskName == null || taskName.isBlank() ? null : taskName);
            String expectedOutput = item.getExpectedOutput() == null ? null : item.getExpectedOutput().trim();
            schedule.setExpectedOutput(expectedOutput == null || expectedOutput.isBlank() ? null : expectedOutput);
            schedule.setExpectedEndDate(item.getExpectedEndDate() == null ? Instant.now() : item.getExpectedEndDate());
            memberScheduleRepository.save(schedule);
            updated++;
        }

        return Map.of("success", true, "updated", updated, "projectId", projectId);
    }

    @Transactional
    public Map<String, Object> confirmMemberSchedule(String projectId, String targetUserId, boolean confirmed) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        String currentUserId = currentUser.getId();
        checkIsProjectManager(projectId, currentUserId);

        ProjectMemberSchedule schedule = memberScheduleRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new BusinessException("未找到该成员的实施任务排期"));

        if (confirmed) {
            if (!Boolean.TRUE.equals(schedule.getCompleted())) {
                schedule.setCompleted(true);
                schedule.setActualEndDate(Instant.now());
            }
            schedule.setManagerConfirmed(true);
            schedule.setManagerConfirmedAt(Instant.now());
        } else {
            schedule.setManagerConfirmed(false);
            schedule.setManagerConfirmedAt(null);
        }

        memberScheduleRepository.save(schedule);
        return Map.of("success", true, "message", confirmed ? "成员任务已确认完成" : "成员任务确认已取消");
    }

    @Transactional(readOnly = true)
    public ByteArrayResource downloadExecutionFile(String projectId, Long fileId) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        ExecutionFile file = findExecutionFileWithPermission(projectId, fileId, currentUser.getId(), true, false);
        try {
            return new ByteArrayResource(Files.readAllBytes(Path.of(file.getFilePath())));
        } catch (IOException ex) {
            throw new BusinessException("文件读取失败: " + ex.getMessage());
        }
    }

    @Transactional
    public void deleteExecutionFile(String projectId, Long fileId) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        ExecutionFile file = findExecutionFileWithPermission(projectId, fileId, currentUser.getId(), false, true);
        executionFileRepository.delete(file);
        try {
            Files.deleteIfExists(Path.of(file.getFilePath()));
        } catch (IOException ex) {
            log.warn("[执行文件] 删除物理文件失败: {}", ex.getMessage());
        }
    }

    @Transactional
    public void recategorizeExecutionFile(String projectId, Long fileId, String secondaryCategory) {
        moveExecutionFileToArchiveFolder(projectId, fileId, secondaryCategory);
    }

    @Transactional
    public Map<String, Object> createManagerArchiveFolder(String projectId, String parentPath, String folderName) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        assertManagerArchiveManageable(projectId, currentUser.getId());

        String folderPath = buildArchiveFolderPath(parentPath, folderName);
        ensureArchiveFolderHierarchy(projectId, folderPath, currentUser.getId());
        return Map.of("success", true, "folderPath", folderPath, "message", "归档子文件夹已创建");
    }

    @Transactional
    public Map<String, Object> moveExecutionFileToArchiveFolder(String projectId, Long fileId, String targetFolderPath) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        assertManagerArchiveManageable(projectId, currentUser.getId());
        ExecutionFile file = findExecutionFileWithPermission(projectId, fileId, currentUser.getId(), false, false);
        if (file.getFolderType() != FolderTypeEnum.A_MANAGER_ARCHIVE || !isProjectManager(projectId, currentUser.getId())) {
            throw new PermissionDeniedException("仅 Manager 可整理管理归档区文件");
        }
        String normalizedTargetFolderPath = normalizeArchiveFolderPath(targetFolderPath);
        ensureArchiveFolderHierarchy(projectId, normalizedTargetFolderPath, currentUser.getId());
        try {
            Path currentPath = Path.of(file.getFilePath());
            Path targetPath = resolveExecutionFilePath(projectId, FolderTypeEnum.A_MANAGER_ARCHIVE, normalizedTargetFolderPath, file.getFileName());
            if (!currentPath.equals(targetPath)) {
                Files.createDirectories(targetPath.getParent());
                if (Files.exists(targetPath)) {
                    throw new BusinessException("目标目录已存在同名文件，请先处理重名文件");
                }
                Files.move(currentPath, targetPath);
                file.setFilePath(targetPath.toString());
            }
        } catch (IOException ex) {
            throw new BusinessException("移动文件失败: " + ex.getMessage());
        }
        file.setSecondaryCategory(normalizedTargetFolderPath == null || normalizedTargetFolderPath.isBlank() ? null : normalizedTargetFolderPath);
        executionFileRepository.save(file);
        return Map.of(
                "success", true,
                "fileId", fileId,
                "targetFolderPath", normalizedTargetFolderPath,
                "message", normalizedTargetFolderPath == null || normalizedTargetFolderPath.isBlank() ? "文件已移动到根目录" : "文件已移动到目标文件夹"
        );
    }

    // ====================================================================
    // Task 3: 结算归档 - OCR 强制拦截与完结
    // POST /api/projects/{projectId}/settlement/complete
    // ====================================================================

    /**
     * 结算归档 - OCR 凭证校验 + 状态跃迁 + 异步归档
     * 
     * 权限：仅当前项目的 MANAGER 可以调用
     * 前置：项目状态必须为 IMPLEMENTING (EXECUTION 阶段)
     * 
     * @param projectId             项目ID (物理隔离键)
     * @param contractVoucherFile   合同/账款凭证截图
     * @return 操作结果
     */
    @Transactional
    public Map<String, Object> completeSettlement(String projectId, MultipartFile contractVoucherFile) {
        // ===== 1. 获取当前登录用户 =====
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }
        String currentUserId = currentUser.getId();

        // ===== 2. 加载项目 =====
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        // ===== 3. 校验项目状态必须为 IMPLEMENTING (EXECUTION 阶段) =====
        if (project.getProjectStatus() != ProjectStatus.IMPLEMENTING) {
            throw new BusinessException("项目当前状态不是实施阶段(EXECUTION)，无法发起结算。当前状态: "
                    + project.getProjectStatus().getStageName());
        }

        // ===== 4. 权限校验：仅 MANAGER 可调用 =====
        checkIsProjectManager(projectId, currentUserId);

        if (projectSubtaskRepository.countByProjectIdAndCompletedFalse(projectId) > 0) {
            throw new BusinessException("仍有未完成子任务，Manager 需确认全部完成后才能进入结算");
        }

        // ===== 5. OCR 强制判断逻辑 =====
        // 调用 Mock OCR 服务校验凭证
        // 如果校验失败会抛出 BusinessException: "凭证识别失败，禁止结算"
        ocrService.verifyVoucherWithAmountCheck(contractVoucherFile, project.getEstimatedRevenue());

        // ===== 6. 保存凭证文件 =====
        String voucherPath = saveVoucherFile(projectId, contractVoucherFile);

        // ===== 7. 状态跃迁：IMPLEMENTING (EXECUTION) → SETTLEMENT =====
        project.setProjectStatus(ProjectStatus.SETTLEMENT);
        projectRepository.save(project);
        log.info("[结算归档] 项目 {} 状态跃迁: IMPLEMENTING → SETTLEMENT，操作人: {}",
                projectId, currentUserId);

        // ===== 8. 触发异步归档事件 (Spring Event) =====
        ProjectSettlementEvent event = new ProjectSettlementEvent(
                this, projectId, currentUserId, voucherPath);
        eventPublisher.publishEvent(event);
        log.info("[结算归档] 已发布 ProjectSettlementEvent，等待异步归档处理");

        return Map.of(
                "success", true,
                "message", "结算完成，项目已进入归档阶段",
                "projectId", projectId,
                "newStatus", ProjectStatus.SETTLEMENT.getStageName()
        );
    }

    // ====================================================================
    // 内部辅助方法
    // ====================================================================

    /**
     * 检查当前用户是否是该项目的 MANAGER
     * 
     * 判断逻辑（满足任一即可）：
     * 1. 用户是 SysProject.manager (项目主负责人)
     * 2. 用户在 SysProjectMember 中角色为 MANAGER
     */
    private void checkIsProjectManager(String projectId, String userId) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser != null && isAdminUser(currentUser.getRole(), currentUser.getUsername())) {
            return;
        }
        if (!isProjectManager(projectId, userId)) {
            throw new PermissionDeniedException("权限不足：仅项目 Manager 可执行此操作");
        }
    }

    /**
     * 判断用户是否是项目 Manager (不抛异常版本)
     */
    private boolean isProjectManager(String projectId, String userId) {
        UserPrincipal currentUser = AuthUtils.getCurrentUserPrincipal();
        if (currentUser != null
                && userId != null
                && userId.equals(currentUser.getId())
                && isAdminUser(currentUser.getRole(), currentUser.getUsername())) {
            return true;
        }
        // 方式1: 是否是项目主负责人 (SysProject.manager)
        boolean isMainManager = projectRepository.isManager(projectId, userId);
        if (isMainManager) return true;

        // 方式2: 是否在成员表中角色为 MANAGER
        return projectMemberRepository.findByProjectIdAndUserUserId(projectId, userId)
                .map(member -> BusinessRoleEnum.MANAGER.name().equals(member.getRole()))
                .orElse(false);
    }

    private void ensureScheduleSlotsForMembers(String projectId) {
        projectMemberRepository.findByProjectId(projectId).forEach(member -> {
            if (isBusinessTaskRole(member.getRole())) {
                return;
            }
            String userId = member.getUser() == null ? null : member.getUser().getUserId();
            if (userId == null || userId.isBlank()) {
                return;
            }
            memberScheduleRepository.findByProjectIdAndUserId(projectId, userId)
                    .orElseGet(() -> memberScheduleRepository.save(ProjectMemberSchedule.builder()
                            .projectId(projectId)
                            .userId(userId)
                            .completed(false)
                            .managerConfirmed(false)
                            .build()));
        });
    }

    private boolean isBusinessTaskRole(String role) {
        String normalized = String.valueOf(role == null ? "" : role).trim().toUpperCase();
        return Set.of("BUSINESS", "BD").contains(normalized);
    }

    private ExecutionOverviewResponseDTO.MemberScheduleInfo buildScheduleInfo(SysProjectMember member, ProjectMemberSchedule schedule) {
        Instant expectedEnd = schedule == null ? null : schedule.getExpectedEndDate();
        Instant actualEnd = schedule == null ? null : schedule.getActualEndDate();
        long delayDays = 0L;
        if (expectedEnd != null) {
            Instant baseline = actualEnd != null ? actualEnd : Instant.now();
            if (baseline.isAfter(expectedEnd)) {
                delayDays = Math.max(1L, Duration.between(expectedEnd, baseline).toDays());
            }
        }
        return ExecutionOverviewResponseDTO.MemberScheduleInfo.builder()
                .userId(member.getUser().getUserId())
                .name(member.getUser().getName())
                .role(member.getRole())
                .taskName(schedule == null ? null : schedule.getTaskName())
                .expectedOutput(schedule == null ? null : schedule.getExpectedOutput())
                .expectedStartDate(formatDateTime(schedule == null ? null : schedule.getExpectedStartDate()))
                .expectedEndDate(formatDateTime(expectedEnd))
                .actualEndDate(formatDateTime(actualEnd))
                .completed(schedule != null && Boolean.TRUE.equals(schedule.getCompleted()))
                .managerConfirmed(schedule != null && Boolean.TRUE.equals(schedule.getManagerConfirmed()))
                .managerConfirmedAt(formatDateTime(schedule == null ? null : schedule.getManagerConfirmedAt()))
                .delayDays(delayDays)
                .executionResponsibilityRatio(member.getWeight())
                .managerResponsibilityRatio(member.getManagerWeight())
                .build();
    }

    private ExecutionOverviewResponseDTO.ExecutionFileInfo toExecutionFileInfo(ExecutionFile file, String currentUserId, boolean isManager) {
        boolean isUploader = currentUserId.equals(file.getUploaderUserId());
        boolean canDelete = file.getFolderType() == FolderTypeEnum.A_MANAGER_ARCHIVE ? isManager : (isManager || isUploader);
        boolean canDownload = file.getFolderType() == FolderTypeEnum.A_MANAGER_ARCHIVE ? isManager : (isManager || isUploader);
        return ExecutionOverviewResponseDTO.ExecutionFileInfo.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .uploaderUserId(file.getUploaderUserId())
                .uploaderName(file.getUploaderName())
                .uploadedAt(formatInstant(file.getUploadedAt()))
                .fileSize(file.getFileSize())
                .folderType(file.getFolderType())
                .secondaryCategory(file.getSecondaryCategory())
                .canDelete(canDelete)
                .canDownload(canDownload)
                .canRecategorize(isManager && file.getFolderType() == FolderTypeEnum.A_MANAGER_ARCHIVE)
                .build();
    }

    private ExecutionFile findExecutionFileWithPermission(String projectId, Long fileId, String currentUserId, boolean forDownload, boolean forDelete) {
        ExecutionFile file = executionFileRepository.findByIdAndProjectId(fileId, projectId)
                .orElseThrow(() -> new BusinessException("执行文件不存在"));
        boolean isManager = isProjectManager(projectId, currentUserId);
        boolean isUploader = currentUserId.equals(file.getUploaderUserId());
        if (file.getFolderType() == FolderTypeEnum.A_MANAGER_ARCHIVE && !isManager) {
            throw new PermissionDeniedException("仅 Manager 可访问管理归档区文件");
        }
        if (file.getFolderType() == FolderTypeEnum.B_ENGINEER_WORK && !(isManager || isUploader)) {
            throw new PermissionDeniedException("实施文件仅对上传者本人或 Manager 可见");
        }
        if (forDelete && file.getFolderType() == FolderTypeEnum.B_ENGINEER_WORK && !(isManager || isUploader)) {
            throw new PermissionDeniedException("仅上传者本人或 Manager 可删除实施文件");
        }
        if (forDownload && file.getFolderType() == FolderTypeEnum.B_ENGINEER_WORK && !(isManager || isUploader)) {
            throw new PermissionDeniedException("仅上传者本人或 Manager 可下载实施文件");
        }
        return file;
    }

    private void assertManagerArchiveManageable(String projectId, String userId) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));
        if (!canUseExecutionWorkspace(project)) {
            throw new BusinessException("当前阶段不可整理实施归档目录");
        }
        if (!isProjectManager(projectId, userId)) {
            throw new PermissionDeniedException("仅 Manager 可整理管理归档区");
        }
    }

    private boolean isExecutionWorkspaceStage(ProjectStatus status) {
        return status == ProjectStatus.IMPLEMENTING || status == ProjectStatus.SETTLEMENT || status == ProjectStatus.COMPLETED;
    }

    private boolean canUseExecutionWorkspace(SysProject project) {
        if (project == null) {
            return false;
        }
        if (project.getFlowType() == FlowType.PRODUCT) {
            ProductStatus status = project.getProductStatus();
            return status == ProductStatus.DEMO_EXECUTION
                    || status == ProductStatus.MEETING_DECISION
                    || status == ProductStatus.TESTING
                    || status == ProductStatus.LAUNCHED;
        }
        if (project.getFlowType() == FlowType.RESEARCH) {
            ResearchStatus status = project.getResearchStatus();
            return status == ResearchStatus.DESIGN
                    || status == ResearchStatus.EXECUTION
                    || status == ResearchStatus.EVALUATION
                    || status == ResearchStatus.ARCHIVE
                    || status == ResearchStatus.PRE_EXECUTION
                    || status == ResearchStatus.CONSTRUCTION
                    || status == ResearchStatus.ARCHIVED_TO_MIDDLEWARE;
        }
        return isExecutionWorkspaceStage(project.getProjectStatus());
    }

    private List<ExecutionOverviewResponseDTO.ArchiveFolderInfo> buildManagerArchiveFolders(String projectId, List<ExecutionFile> managerFiles) {
        List<ExecutionArchiveFolder> storedFolders = executionArchiveFolderRepository
                .findByProjectIdAndFolderTypeOrderByFolderPathAsc(projectId, FolderTypeEnum.A_MANAGER_ARCHIVE);
        Map<String, ExecutionArchiveFolder> folderByPath = new TreeMap<>();
        Set<String> folderPaths = new TreeSet<>();

        storedFolders.forEach(folder -> {
            String normalizedPath = normalizeArchiveFolderPath(folder.getFolderPath());
            if (normalizedPath.isBlank()) {
                return;
            }
            folderByPath.put(normalizedPath, folder);
            addArchivePathHierarchy(folderPaths, normalizedPath);
        });
        managerFiles.forEach(file -> addArchivePathHierarchy(folderPaths, normalizeArchiveFolderPath(file.getSecondaryCategory())));

        return folderPaths.stream()
                .map(path -> {
                    ExecutionArchiveFolder storedFolder = folderByPath.get(path);
                    return ExecutionOverviewResponseDTO.ArchiveFolderInfo.builder()
                            .id(storedFolder == null ? null : storedFolder.getId())
                            .folderPath(path)
                            .folderName(extractArchiveFolderName(path))
                            .parentPath(extractArchiveParentPath(path))
                            .canManage(true)
                            .build();
                })
                .toList();
    }

    private void addArchivePathHierarchy(Set<String> folderPaths, String folderPath) {
        String normalizedPath = normalizeArchiveFolderPath(folderPath);
        if (normalizedPath.isBlank()) {
            return;
        }
        String[] segments = normalizedPath.split("/");
        StringBuilder current = new StringBuilder();
        for (String segment : segments) {
            if (current.length() > 0) {
                current.append('/');
            }
            current.append(segment);
            folderPaths.add(current.toString());
        }
    }

    private void ensureArchiveFolderHierarchy(String projectId, String folderPath, String currentUserId) {
        String normalizedPath = normalizeArchiveFolderPath(folderPath);
        if (normalizedPath.isBlank()) {
            return;
        }
        String[] segments = normalizedPath.split("/");
        StringBuilder current = new StringBuilder();
        for (String segment : segments) {
            if (current.length() > 0) {
                current.append('/');
            }
            current.append(segment);
            String currentPath = current.toString();
            if (!executionArchiveFolderRepository.existsByProjectIdAndFolderTypeAndFolderPath(projectId, FolderTypeEnum.A_MANAGER_ARCHIVE, currentPath)) {
                executionArchiveFolderRepository.save(ExecutionArchiveFolder.builder()
                        .projectId(projectId)
                        .folderType(FolderTypeEnum.A_MANAGER_ARCHIVE)
                        .folderPath(currentPath)
                        .parentPath(extractArchiveParentPath(currentPath))
                        .createdByUserId(currentUserId)
                        .build());
            }
            try {
                Files.createDirectories(resolveExecutionFolderPath(projectId, FolderTypeEnum.A_MANAGER_ARCHIVE, currentPath));
            } catch (IOException ex) {
                throw new BusinessException("创建归档目录失败: " + ex.getMessage());
            }
        }
    }

    private String buildArchiveFolderPath(String parentPath, String folderName) {
        String normalizedParentPath = normalizeArchiveFolderPath(parentPath);
        String normalizedFolderName = normalizeArchiveFolderName(folderName);
        return normalizedParentPath.isBlank() ? normalizedFolderName : normalizedParentPath + "/" + normalizedFolderName;
    }

    private String normalizeArchiveFolderName(String folderName) {
        String normalized = folderName == null ? "" : folderName.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("文件夹名称不能为空");
        }
        if (normalized.contains("/") || normalized.contains("\\")) {
            throw new BusinessException("文件夹名称不能包含斜杠");
        }
        if (".".equals(normalized) || "..".equals(normalized)) {
            throw new BusinessException("文件夹名称不合法");
        }
        return normalized;
    }

    private String normalizeArchiveFolderPath(String folderPath) {
        String normalized = folderPath == null ? "" : folderPath.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return "";
        }
        String[] segments = normalized.split("/");
        List<String> normalizedSegments = new ArrayList<>();
        for (String segment : segments) {
            String trimmedSegment = segment == null ? "" : segment.trim();
            if (trimmedSegment.isBlank() || ".".equals(trimmedSegment) || "..".equals(trimmedSegment)) {
                throw new BusinessException("文件夹路径不合法");
            }
            normalizedSegments.add(trimmedSegment);
        }
        String normalizedPath = String.join("/", normalizedSegments);
        if (normalizedPath.length() > 255) {
            throw new BusinessException("文件夹路径过长");
        }
        return normalizedPath;
    }

    private String extractArchiveFolderName(String folderPath) {
        String normalizedPath = normalizeArchiveFolderPath(folderPath);
        if (normalizedPath.isBlank()) {
            return "根目录";
        }
        int index = normalizedPath.lastIndexOf('/');
        return index < 0 ? normalizedPath : normalizedPath.substring(index + 1);
    }

    private String extractArchiveParentPath(String folderPath) {
        String normalizedPath = normalizeArchiveFolderPath(folderPath);
        if (normalizedPath.isBlank()) {
            return null;
        }
        int index = normalizedPath.lastIndexOf('/');
        if (index < 0) {
            return null;
        }
        return normalizedPath.substring(0, index);
    }

    private Path resolveExecutionFolderPath(String projectId, FolderTypeEnum folderType, String folderPath) {
        Path basePath = Paths.get(System.getProperty("user.dir"), "uploads", "execution", projectId, folderType.name());
        String normalizedFolderPath = normalizeArchiveFolderPath(folderPath);
        if (normalizedFolderPath.isBlank()) {
            return basePath;
        }
        return basePath.resolve(normalizedFolderPath);
    }

    private Path resolveExecutionFilePath(String projectId, FolderTypeEnum folderType, String folderPath, String fileName) {
        return resolveExecutionFolderPath(projectId, folderType, folderPath).resolve(fileName);
    }

    private String normalizeSecondaryCategory(String secondaryCategory) {
        if (secondaryCategory == null) {
            return null;
        }
        String normalized = normalizeArchiveFolderPath(secondaryCategory);
        return normalized.isBlank() ? null : normalized;
    }

    private Set<String> sanitizeIds(List<String> ids) {
        if (ids == null) {
            return Set.of();
        }
        return ids.stream()
                .map(id -> id == null ? "" : id.trim())
                .filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String formatInstant(Instant instant) {
        return instant == null ? null : DATE_FORMATTER.format(instant);
    }

    private String formatDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(instant);
    }

    /**
     * 保存凭证文件到项目专属目录
     */
    private String saveVoucherFile(String projectId, MultipartFile file) {
        String uploadDir = System.getProperty("user.dir") + "/uploads/settlement/" + projectId + "/";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) originalFilename = "voucher_" + System.currentTimeMillis();

        try {
            File directory = new File(uploadDir);
            if (!directory.exists()) directory.mkdirs();

            Path targetPath = Paths.get(uploadDir + originalFilename);
            file.transferTo(targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            throw new BusinessException("凭证文件存储失败: " + e.getMessage());
        }
    }

    /**
     * 安全解析 ISO-8601 日期字符串为 Instant
     * 如果解析失败返回 null，不抛异常
     */
    private Instant parseInstant(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            log.warn("[日期解析] 无法解析日期字符串: {}，返回 null", dateStr);
            return null;
        }
    }
}
