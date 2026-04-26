package com.smartlab.erp.controller;

import com.smartlab.erp.dto.ProductIdeaRequest;
import com.smartlab.erp.dto.ProductMemberUpdateRequest;
import com.smartlab.erp.dto.ProductPromotionSetupRequest;
import com.smartlab.erp.dto.ProductTaskAssignmentDTO;
import com.smartlab.erp.dto.ProductTaskAssignmentUpdateRequest;
import com.smartlab.erp.dto.WorkflowMemberRoleDTO;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.enums.DemoFileType;
import com.smartlab.erp.service.ProductFlowService;
import com.smartlab.erp.service.WorkflowMemberRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.List;

/**
 * 产品流 / 软件流 控制器（轨道 B）
 *
 * 负责内部创投型产品从 Idea 到 Demo、会议决策的主流程编排。
 *
     * 路由前缀统一为 /api/products：
     * 1. Idea 创意孵化        → POST /api/products/idea
     * 2. 推广与 Demo 组队      → POST /api/products/{projectId}/promotion-setup
     * 3. Demo 实施上传网关    → POST /api/products/{projectId}/demo/upload
     * 4. 虚拟会议决策        → POST /api/products/{projectId}/meeting-decision
     * 5. 测试反馈与正式上线网关 → POST /api/products/{projectId}/testing-feedback
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProductFlowController {

    private final ProductFlowService productFlowService;
    private final WorkflowMemberRoleService workflowMemberRoleService;

    // === Task 2: Idea 创意孵化 =============================================

    @PostMapping("/idea")
    public ResponseEntity<SysProject> createIdea(@RequestBody ProductIdeaRequest request) {
        SysProject project = productFlowService.createIdea(request);
        return ResponseEntity.ok(project);
    }

    // === Task 3: 推广与 Demo 组队 ==========================================

    @PostMapping("/{projectId}/promotion-setup")
    public ResponseEntity<SysProject> setupPromotionTeam(
            @PathVariable String projectId,
            @RequestBody ProductPromotionSetupRequest request) {
        SysProject project = productFlowService.setupPromotionTeam(projectId, request);
        return ResponseEntity.ok(project);
    }

    // === Task 4: Demo 实施与自动跃迁网关 ==================================

    @PostMapping("/{projectId}/demo/upload")
    public ResponseEntity<Map<String, Object>> uploadDemoAsset(
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("demoFileType") DemoFileType demoFileType) {
        Map<String, Object> result = productFlowService.uploadDemoAsset(projectId, file, demoFileType);
        return ResponseEntity.ok(result);
    }

    // === Task 1: 虚拟会议决策 (Meeting Decision) ============================

    @PostMapping("/{projectId}/meeting-decision")
    public ResponseEntity<Map<String, Object>> submitMeetingDecision(
            @PathVariable String projectId,
            @RequestParam("meetingMinutes") MultipartFile meetingMinutes,
            @RequestParam("decision") String decision,
            @RequestParam("participantUserIds") java.util.List<String> participantUserIds) {
        Map<String, Object> result = productFlowService.submitMeetingDecision(projectId, meetingMinutes, decision, participantUserIds);
        return ResponseEntity.ok(result);
    }

    // === Task 2: 测试与正式上线网关 (Testing & Launch) =====================

    @PostMapping("/{projectId}/testing-feedback")
    public ResponseEntity<Map<String, Object>> submitTestingFeedback(
            @PathVariable String projectId,
            @RequestParam("testFeedback") String testFeedback,
            @RequestParam("isPassed") boolean isPassed) {
        Map<String, Object> result = productFlowService.submitTestingFeedback(projectId, testFeedback, isPassed);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{projectId}/team-members")
    public ResponseEntity<Map<String, Object>> updateProductTeamMembers(
            @PathVariable String projectId,
            @RequestBody ProductMemberUpdateRequest request) {
        return ResponseEntity.ok(productFlowService.updateProductMembers(projectId, request));
    }

    @GetMapping("/{projectId}/task-assignments")
    public ResponseEntity<List<ProductTaskAssignmentDTO>> getTaskAssignments(@PathVariable String projectId) {
        return ResponseEntity.ok(productFlowService.getTaskAssignments(projectId));
    }

    @PutMapping("/{projectId}/task-assignments")
    public ResponseEntity<Map<String, Object>> updateTaskAssignments(
            @PathVariable String projectId,
            @RequestBody ProductTaskAssignmentUpdateRequest request) {
        return ResponseEntity.ok(productFlowService.updateTaskAssignments(projectId, request));
    }

    @GetMapping("/{projectId}/workflow-member-roles")
    public ResponseEntity<List<WorkflowMemberRoleDTO>> getWorkflowMemberRoles(@PathVariable String projectId) {
        return ResponseEntity.ok(workflowMemberRoleService.getWorkflowMemberRoles("PRODUCT", projectId));
    }

    @GetMapping("/role-candidates")
    public ResponseEntity<List<WorkflowMemberRoleDTO>> getRoleCandidates() {
        return ResponseEntity.ok(workflowMemberRoleService.getWorkflowRoleCandidates("PRODUCT"));
    }

}
