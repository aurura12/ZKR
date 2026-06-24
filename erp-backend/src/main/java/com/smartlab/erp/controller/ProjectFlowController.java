package com.smartlab.erp.controller;

import com.smartlab.erp.dto.ExecutionOverviewResponseDTO;
import com.smartlab.erp.dto.ExecutionPlanRequestDTO;
import com.smartlab.erp.dto.ProjectDynamicInfoUpdateRequest;
import com.smartlab.erp.dto.ProjectTaskAssignmentDTO;
import com.smartlab.erp.dto.ProjectTaskAssignmentUpdateRequest;
import com.smartlab.erp.dto.ProductMemberUpdateRequest;
import com.smartlab.erp.dto.ProjectBuildTeamRequestDTO;
import com.smartlab.erp.dto.ProjectInitiateRequestDTO;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.enums.FolderTypeEnum;
import com.smartlab.erp.service.ProjectFlowService;
import com.smartlab.erp.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.List;

/**
 * 项目流控制器
 *
 * 统一管理项目流(Project Flow)全生命周期的 API 接口：
 * 1. 发起阶段 (INITIATION)   → POST /api/projects/initiate
 * 2. 组队阶段 (TEAM_FORMATION) → POST /api/projects/{projectId}/build-team
 * 3. 实施阶段 (EXECUTION)     → POST /api/projects/{projectId}/execution/plan
 *                              → POST /api/projects/{projectId}/execution/upload
 * 4. 结算归档 (SETTLEMENT)    → POST /api/projects/{projectId}/settlement/complete
 *
 * 设计原则：
 * - 无审批流，全部依靠特定角色的"动作触发"进行状态跃迁
 * - 所有操作通过 projectId 物理隔离
 * - 权限校验在 Service 层实现
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectFlowController {

    /** 复用已有的项目服务 (发起、组队逻辑已实现) */
    private final ProjectService projectService;

    /** 新的项目流服务 (实施、结算逻辑) */
    private final ProjectFlowService projectFlowService;

    // ====================================================================
    // API 1: 商务发起项目 (Initiation)
    // ====================================================================

    /**
     * 商务发起项目
     *
     * 接口路径: POST /api/projects/initiate
     * 权限: 仅 BD (商务) 角色可调用
     * 
     * 业务流程:
     * 1. 创建项目主记录
     * 2. 写入 BD 和数据工程师到成员表
     * 3. 状态跃迁: → TEAM_FORMATION (组队阶段)
     *
     * @param request 项目发起请求 (项目名称、预计收入、数据工程师ID、可行性报告、评级)
     * @return 创建后的项目实体
     */
    @PostMapping("/initiate")
    public ResponseEntity<SysProject> initiateProject(@RequestBody ProjectInitiateRequestDTO request) {
        // 委托给已有的 ProjectService.initiateProject()
        // 内部已包含 BD 角色校验和状态跃迁逻辑
        SysProject savedProject = projectService.initiateProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProject);
    }

    // ====================================================================
    // API 2: 数据工程师组建团队 (Team Formation)
    // ====================================================================

    /**
     * 数据工程师组建团队
     *
     * 接口路径: POST /api/projects/{projectId}/build-team
     * 权限: 仅该项目的 DATA_ENGINEER 可调用
     * 
     * 业务流程:
     * 1. 校验 managerUserId 必须是 BD 或 DATA_ENGINEER
     * 2. 批量写入团队成员到关联表
     * 3. 处理 DATA_ENGINEER 兼任 MANAGER 的双重角色
     * 4. 状态跃迁: TEAM_FORMATION → IMPLEMENTING (EXECUTION)
     *
     * @param projectId 项目ID
     * @param request   组建团队请求 (团队成员列表、Manager用户ID)
     * @return 更新后的项目实体
     */
    @PostMapping("/{projectId}/build-team")
    public ResponseEntity<SysProject> buildTeam(
            @PathVariable String projectId,
            @RequestBody ProjectBuildTeamRequestDTO request) {
        // 委托给已有的 ProjectService.buildTeam()
        // 内部已包含 DATA_ENGINEER 角色校验、Manager 规则校验和状态跃迁
        SysProject updatedProject = projectService.buildTeam(projectId, request);
        return ResponseEntity.ok(updatedProject);
    }

    /**
     * jiaomiao 专属：摇摆变更项目 Manager
     *
     * 接口路径: POST /api/projects/{projectId}/swap-manager
     * 权限: 仅 userId=000027 (jiaomiao) 可调用
     *
     * 业务流程:
     * 1. 校验调用者为 jiaomiao
     * 2. 现任 Manager 的管理权责比自动转移至另一创始成员
     * 3. 更新 project.manager_id
     *
     * @param projectId 项目ID
     */
    @PostMapping("/{projectId}/swap-manager")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> swapManager(@PathVariable String projectId) {
        projectService.swapProjectManager(projectId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Manager 已摇摆变更"));
    }

    // ====================================================================
    // API 3: Manager 目标管控与进度设定 (Execution - Plan)
    // ====================================================================

    /**
     * Manager 设定实施计划
     *
     * 接口路径: POST /api/projects/{projectId}/execution/plan
     * 权限: 仅当前项目的 MANAGER 可调用
     * 
     * 业务流程:
     * 1. 校验项目状态必须为 IMPLEMENTING (EXECUTION)
     * 2. 持久化难度分级和技术栈描述到扩展表
     * 3. 持久化每个成员的排期到扩展表
     *
     * @param projectId 项目ID
     * @param request   实施计划请求 (难度、技术栈、成员排期列表)
     * @return 操作结果
     */
    @PostMapping("/{projectId}/execution/plan")
    public ResponseEntity<Map<String, Object>> setExecutionPlan(
            @PathVariable String projectId,
            @RequestBody ExecutionPlanRequestDTO request) {
        Map<String, Object> result = projectFlowService.setExecutionPlan(projectId, request);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{projectId}/execution/team-members")
    public ResponseEntity<Map<String, Object>> updateExecutionTeamMembers(
            @PathVariable String projectId,
            @RequestBody ProductMemberUpdateRequest request) {
        return ResponseEntity.ok(projectFlowService.updateExecutionMembers(projectId, request));
    }

    @GetMapping("/{projectId}/task-assignments")
    public ResponseEntity<List<ProjectTaskAssignmentDTO>> getProjectTaskAssignments(@PathVariable String projectId) {
        return ResponseEntity.ok(projectFlowService.getProjectTaskAssignments(projectId));
    }

    @PutMapping("/{projectId}/task-assignments")
    public ResponseEntity<Map<String, Object>> updateProjectTaskAssignments(@PathVariable String projectId,
                                                                            @RequestBody ProjectTaskAssignmentUpdateRequest request) {
        return ResponseEntity.ok(projectFlowService.updateProjectTaskAssignments(projectId, request));
    }

    @PatchMapping("/{projectId}/implementation-status")
    public ResponseEntity<Map<String, Object>> updateImplementationStatus(@PathVariable String projectId,
                                                                          @RequestBody Map<String, String> payload) {
        var principal = com.smartlab.erp.util.AuthUtils.getCurrentUserPrincipal();
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "未登录"));
        }
        String userId = principal.getId();
        String statusText = payload == null ? null : payload.get("status");
        return ResponseEntity.ok(projectService.updateImplementationStatus(projectId, statusText, userId));
    }

    @PatchMapping("/{projectId}/dynamic-info")
    public ResponseEntity<Map<String, Object>> updateProjectDynamicInfo(@PathVariable String projectId,
                                                                        @RequestBody ProjectDynamicInfoUpdateRequest request) {
        var principal = com.smartlab.erp.util.AuthUtils.getCurrentUserPrincipal();
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "未登录"));
        }
        return ResponseEntity.ok(projectService.updateProjectDynamicInfo(projectId, request, principal.getId(), principal.getRole(), principal.getUsername()));
    }

    @GetMapping("/{projectId}/execution/overview")
    public ResponseEntity<ExecutionOverviewResponseDTO> getExecutionOverview(@PathVariable String projectId) {
        return ResponseEntity.ok(projectFlowService.getExecutionOverview(projectId));
    }

    @PatchMapping("/{projectId}/execution/schedules/{userId}/confirm")
    public ResponseEntity<Map<String, Object>> confirmMemberSchedule(@PathVariable String projectId,
                                                                     @PathVariable String userId,
                                                                     @RequestBody(required = false) Map<String, Object> payload) {
        boolean confirmed = true;
        if (payload != null && payload.containsKey("confirmed")) {
            Object value = payload.get("confirmed");
            if (value instanceof Boolean b) {
                confirmed = b;
            } else if (value != null) {
                confirmed = Boolean.parseBoolean(String.valueOf(value));
            }
        }
        return ResponseEntity.ok(projectFlowService.confirmMemberSchedule(projectId, userId, confirmed));
    }

    // ====================================================================
    // API 4: 强制双盲文件隔离上传 (Execution - Upload)
    // ====================================================================

    /**
     * 实施阶段双盲隔离文件上传
     *
     * 接口路径: POST /api/projects/{projectId}/execution/upload
     * 权限:
     *   - folderType = A_MANAGER_ARCHIVE → 仅 MANAGER 可上传
     *   - folderType = B_ENGINEER_WORK   → 仅非 Manager 的项目成员可上传
     * 
     * 文件物理路径按 projectId/folderType 隔离存储。
     *
     * @param projectId  项目ID
     * @param file       上传文件
     * @param folderType 目标隔离区 (A_MANAGER_ARCHIVE / B_ENGINEER_WORK)
     * @return 上传结果
     */
    @PostMapping("/{projectId}/execution/upload")
    public ResponseEntity<Map<String, Object>> uploadExecutionFile(
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("folderType") FolderTypeEnum folderType,
            @RequestParam(value = "secondaryCategory", required = false) String secondaryCategory) {
        Map<String, Object> result = projectFlowService.uploadExecutionFile(projectId, file, folderType, secondaryCategory);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{projectId}/execution/files/{fileId}/download")
    public ResponseEntity<ByteArrayResource> downloadExecutionFile(@PathVariable String projectId,
                                                                   @PathVariable Long fileId) {
        ByteArrayResource resource = projectFlowService.downloadExecutionFile(projectId, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=execution-file-" + fileId)
                .body(resource);
    }

    @DeleteMapping("/{projectId}/execution/files/{fileId}")
    public ResponseEntity<Void> deleteExecutionFile(@PathVariable String projectId,
                                                    @PathVariable Long fileId) {
        projectFlowService.deleteExecutionFile(projectId, fileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/execution/archive-folders")
    public ResponseEntity<Map<String, Object>> createManagerArchiveFolder(@PathVariable String projectId,
                                                                          @RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(projectFlowService.createManagerArchiveFolder(
                projectId,
                payload == null ? null : payload.get("parentPath"),
                payload == null ? null : payload.get("folderName")
        ));
    }

    @PatchMapping("/{projectId}/execution/files/{fileId}/archive-folder")
    public ResponseEntity<Map<String, Object>> moveExecutionFileToArchiveFolder(@PathVariable String projectId,
                                                                                 @PathVariable Long fileId,
                                                                                 @RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(projectFlowService.moveExecutionFileToArchiveFolder(
                projectId,
                fileId,
                payload == null ? null : payload.get("targetFolderPath")
        ));
    }

    @PatchMapping("/{projectId}/execution/files/{fileId}/category")
    public ResponseEntity<Map<String, Object>> recategorizeExecutionFile(@PathVariable String projectId,
                                                                         @PathVariable Long fileId,
                                                                         @RequestBody Map<String, String> payload) {
        projectFlowService.recategorizeExecutionFile(projectId, fileId, payload.get("secondaryCategory"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ====================================================================
    // API 5: 结算归档 - OCR 强制拦截与完结 (Settlement)
    // ====================================================================

    /**
     * 结算归档 - OCR 凭证校验后完结项目
     *
     * 接口路径: POST /api/projects/{projectId}/settlement/complete
     * 权限: 仅当前项目的 MANAGER 可调用
     * 
     * 业务流程:
     * 1. 校验项目状态必须为 IMPLEMENTING (EXECUTION)
     * 2. 调用 OCR 服务校验凭证 (失败则抛出异常，禁止结算)
     * 3. 状态跃迁: IMPLEMENTING → SETTLEMENT
     * 4. 发布 ProjectSettlementEvent 异步归档
     *
     * @param projectId             项目ID
     * @param contractVoucherFile   合同/账款凭证截图
     * @return 操作结果
     */
    @PostMapping("/{projectId}/settlement/complete")
    public ResponseEntity<Map<String, Object>> completeSettlement(
            @PathVariable String projectId,
            @RequestParam("contractVoucherFile") MultipartFile contractVoucherFile) {
        Map<String, Object> result = projectFlowService.completeSettlement(projectId, contractVoucherFile);
        return ResponseEntity.ok(result);
    }
}
