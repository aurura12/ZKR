package com.smartlab.erp.controller;

import com.smartlab.erp.dto.ResearchInitiateRequest;
import com.smartlab.erp.dto.ResearchStatusTransitionRequest;
import com.smartlab.erp.dto.WorkflowMemberRoleDTO;
import com.smartlab.erp.entity.MiddlewareAsset;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.service.ResearchFlowService;
import com.smartlab.erp.service.WorkflowMemberRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 科研流（Research Flow）控制器
 *
 * 路由前缀统一为 /api/research：
 * 1. 发起与初探           → POST /api/research/initiate
 * 2. 施工执行模式切换      → POST /api/research/{projectId}/set-construction-mode
 * 3. 评测入库生成中间件资产 → POST /api/research/{projectId}/archive-to-middleware
 */
@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ResearchFlowController {

    private final ResearchFlowService researchFlowService;
    private final WorkflowMemberRoleService workflowMemberRoleService;

    // === Task 2: 阶段 1 - 发起与初探 =======================================

    @PostMapping("/initiate")
    public ResponseEntity<SysProject> initiateResearch(@RequestBody ResearchInitiateRequest request) {
        SysProject project = researchFlowService.initiateResearch(request);
        return ResponseEntity.ok(project);
    }

    // === Task 3: 阶段 4 - 施工执行模式切换 ==================================

    @PostMapping("/{projectId}/set-construction-mode")
    public ResponseEntity<Void> setConstructionMode(
            @PathVariable String projectId,
            @RequestParam("executionMode") String executionMode) {
        researchFlowService.setConstructionMode(projectId, executionMode);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{projectId}/transition")
    public ResponseEntity<Map<String, Object>> transitionStatus(
            @PathVariable String projectId,
            @RequestBody ResearchStatusTransitionRequest request) {
        return ResponseEntity.ok(researchFlowService.transitionStatus(projectId, request));
    }

    // === Task 4: 终极网关 - 评测入库生成中间件 ===============================

    @PostMapping("/{projectId}/archive-to-middleware")
    public ResponseEntity<MiddlewareAsset> archiveToMiddleware(
            @PathVariable String projectId,
            @RequestParam("middlewareName") String middlewareName,
            @RequestParam("middlewareDesc") String middlewareDesc,
            @RequestParam("repoUrl") String repoUrl) {
        MiddlewareAsset asset = researchFlowService.archiveToMiddleware(projectId, middlewareName, middlewareDesc, repoUrl);
        return ResponseEntity.ok(asset);
    }

    @PostMapping("/{projectId}/upload-key-doc")
    public ResponseEntity<Map<String, Object>> uploadResearchKeyDoc(
            @PathVariable String projectId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("docType") String docType) {
        Map<String, Object> result = researchFlowService.uploadResearchKeyDoc(projectId, file, docType);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{projectId}/workflow-member-roles")
    public ResponseEntity<java.util.List<WorkflowMemberRoleDTO>> getWorkflowMemberRoles(@PathVariable String projectId) {
        return ResponseEntity.ok(workflowMemberRoleService.getWorkflowMemberRoles("RESEARCH", projectId));
    }

    @GetMapping("/role-candidates")
    public ResponseEntity<java.util.List<WorkflowMemberRoleDTO>> getRoleCandidates() {
        return ResponseEntity.ok(workflowMemberRoleService.getResearchRoleCandidates());
    }
}
