package com.smartlab.erp.controller;

import com.smartlab.erp.config.CompanyProjectInitializer;
import com.smartlab.erp.dto.AdjustProjectCostRequest;
import com.smartlab.erp.dto.CreateProjectRequest;
import com.smartlab.erp.dto.FinanceDashboardResponse;
import com.smartlab.erp.dto.ManagedProjectsSummaryResponse;
import com.smartlab.erp.dto.ProjectDetailResponse;
import com.smartlab.erp.dto.ProjectMemberEarningsResponse;
import com.smartlab.erp.dto.ProjectSubtaskRequest;
import com.smartlab.erp.dto.ProjectSubtaskResponse;
import com.smartlab.erp.dto.ReviewExpenseRequest;
import com.smartlab.erp.dto.SubmitProjectExpenseRequest;
import com.smartlab.erp.dto.WorkflowMemberRoleDTO;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.ProjectCostAdjustment;
import com.smartlab.erp.entity.ProjectExpense;
import com.smartlab.erp.entity.ProjectStatus;
import com.smartlab.erp.entity.ProductStatus;
import com.smartlab.erp.finance.dto.FinanceExpenseSubmissionCreateRequest;
import com.smartlab.erp.finance.entity.FinanceExpenseSubmission;
import com.smartlab.erp.finance.service.FinanceExpenseSubmissionService;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.service.ProjectFinancialMetricsService;
import com.smartlab.erp.service.ProjectService;
import com.smartlab.erp.service.ReimbursementZipService;
import com.smartlab.erp.service.WorkflowMemberRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final FinanceExpenseSubmissionService financeExpenseSubmissionService;
    private final ProjectFinancialMetricsService projectFinancialMetricsService;
    private final WorkflowMemberRoleService workflowMemberRoleService;
    private final ReimbursementZipService reimbursementZipService;

    // ================== 🟢 核心修复：列表查询接口 ==================

    /**
     * ✅ [救命补丁] 根路径查询 (GET /api/projects)
     * 作用：解决 "Request method 'GET' is not supported" 报错。
     * 逻辑：如果前端请求了根路径，默认返回“我参与的项目”作为兜底。
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SysProject>> getAllProjectsFallback(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(projectService.getParticipatedProjects(currentUser));
    }

    /**
     * ✅ 管理看板专用 (GET /api/projects/managed)
     */
    @GetMapping("/managed")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SysProject>> getManagedProjects(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(projectService.getManagedProjects(currentUser));
    }

    @GetMapping("/managed/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ManagedProjectsSummaryResponse> getManagedProjectsSummary(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(projectService.getManagedProjectsSummary(currentUser));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinanceDashboardResponse> getFinanceDashboard() {
        return ResponseEntity.ok(projectService.getFinanceDashboard());
    }

    /**
     * ✅ 工作区侧边栏专用 (GET /api/projects/workspace)
     */
    @GetMapping("/workspace")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SysProject>> getParticipatedProjects(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(projectService.getParticipatedProjects(currentUser));
    }

    /**
     * ✅ 获取单个详情 (GET /api/projects/{id})
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectDetailResponse> getProject(@PathVariable String id,
                                                            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(projectService.getProjectDetail(id, currentUser));
    }

    @GetMapping("/{id}/workflow-member-roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WorkflowMemberRoleDTO>> getWorkflowMemberRoles(@PathVariable String id) {
        return ResponseEntity.ok(workflowMemberRoleService.getWorkflowMemberRoles("PROJECT", id));
    }

    @GetMapping("/role-candidates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WorkflowMemberRoleDTO>> getRoleCandidates() {
        return ResponseEntity.ok(workflowMemberRoleService.getWorkflowRoleCandidates("PROJECT"));
    }

    @GetMapping("/{id}/earnings/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectMemberEarningsResponse> getMyProjectEarnings(@PathVariable String id,
                                                                              @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(projectFinancialMetricsService.getMyProjectEarnings(id, currentUser));
    }

    // ================== 创建与修改接口 ==================

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SysProject> createProject(@RequestBody CreateProjectRequest request,
                                                    @AuthenticationPrincipal UserPrincipal currentUser) {
        SysProject saved = projectService.createProject(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}/critical-task")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateCriticalTask(
            @PathVariable String id,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        String task = payload.get("critical_task");
        projectService.updateCriticalTask(id, task, currentUser.getId());
        return ResponseEntity.ok().build();
    }

    // ================== 文件上传接口 ==================

    @PostMapping("/{id}/assets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadFile(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "assetCategory", required = false) String assetCategory,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        projectService.uploadProjectAsset(id, file, currentUser.getId(), assetCategory);
        return ResponseEntity.ok().body(Map.of("success", true, "message", "文件上传成功"));
    }

    @PostMapping(value = "/{id}/travel-reimbursements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> submitTravelReimbursement(
            @PathVariable String id,
            @Valid @ModelAttribute FinanceExpenseSubmissionCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        FinanceExpenseSubmission submission = financeExpenseSubmissionService.submitProjectTravelReimbursement(id, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", submission.getId(),
                "status", submission.getStatus().name(),
                "type", submission.getSubmissionType().name(),
                "message", "出差报销已提交至财务系统"
        ));
    }

    @PostMapping(value = "/{id}/project-expenses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> submitProjectExpense(
            @PathVariable String id,
            @Valid @ModelAttribute FinanceExpenseSubmissionCreateRequest request,
            @RequestParam("expenseType") String expenseType,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        com.smartlab.erp.finance.enums.FinanceExpenseSubmissionType type;
        try {
            type = com.smartlab.erp.finance.enums.FinanceExpenseSubmissionType.valueOf(expenseType);
        } catch (IllegalArgumentException e) {
            throw new com.smartlab.erp.exception.BusinessException("不支持的成本类型: " + expenseType);
        }
        FinanceExpenseSubmission submission = financeExpenseSubmissionService.submitProjectExpense(id, request, currentUser.getId(), type);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", submission.getId(),
                "status", submission.getStatus().name(),
                "type", submission.getSubmissionType().name(),
                "message", "项目成本已提交"
        ));
    }

    @GetMapping("/{id}/assets/{assetId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> downloadFile(
            @PathVariable String id,
            @PathVariable Long assetId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        var asset = projectService.getProjectAsset(id, assetId, currentUser.getId());
        byte[] fileBytes = asset.getFileData();
        if (fileBytes == null || fileBytes.length == 0) {
            try {
                fileBytes = Files.readAllBytes(Path.of(asset.getFilePath()));
            } catch (Exception e) {
                return ResponseEntity.notFound().build();
            }
        }

        ByteArrayResource resource = new ByteArrayResource(fileBytes);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (asset.getContentType() != null && !asset.getContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(asset.getContentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + asset.getFileName() + "\"")
                .contentLength(asset.getFileSize() != null ? asset.getFileSize() : fileBytes.length)
                .body(resource);
    }

    // ================== 里程碑接口 ==================

    @PostMapping("/{id}/milestones")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addMilestone(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String title = (String) payload.get("title");
        String dateStr = (String) payload.get("dueDate");

        projectService.addMilestone(id, title, dateStr, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/subtasks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectSubtaskResponse> createSubtask(@PathVariable String id,
                                                                @Valid @RequestBody ProjectSubtaskRequest request,
                                                                @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createSubtask(id, request, currentUser.getId()));
    }

    @PutMapping("/{id}/subtasks/{subtaskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectSubtaskResponse> updateSubtask(@PathVariable String id,
                                                                @PathVariable Long subtaskId,
                                                                @Valid @RequestBody ProjectSubtaskRequest request,
                                                                @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(projectService.updateSubtask(id, subtaskId, request, currentUser.getId()));
    }

    @PostMapping("/{id}/subtasks/{subtaskId}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> completeSubtask(@PathVariable String id,
                                                               @PathVariable Long subtaskId,
                                                               @AuthenticationPrincipal UserPrincipal currentUser) {
        projectService.completeSubtask(id, subtaskId, currentUser.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ================== 状态流转接口 ==================

    @PutMapping("/{id}/project-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> transitionProjectStatus(
            @PathVariable String id,
            @RequestParam ProjectStatus status,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        projectService.transitionProjectStatus(id, status, currentUser.getId());
        return ResponseEntity.ok().body(Map.of("success", true, "newStatus", status));
    }

    @PutMapping("/{id}/product-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> transitionProductStatus(
            @PathVariable String id,
            @RequestParam ProductStatus status,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        projectService.transitionProductStatus(id, status, currentUser.getId());
        return ResponseEntity.ok().body(Map.of("success", true, "newStatus", status));
    }

    @PostMapping("/{projectId}/expenses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> submitProjectExpense(
            @PathVariable String projectId,
            @RequestParam("expenseType") String expenseType,
            @RequestParam("itemName") String itemName,
            @RequestParam("amount") String amount,
            @RequestParam(value = "counterparty", required = false) String counterparty,
            @RequestParam(value = "invoiceFiles", required = false) List<MultipartFile> invoiceFiles) {
        SubmitProjectExpenseRequest request = new SubmitProjectExpenseRequest();
        request.setExpenseType(expenseType);
        request.setItemName(itemName);
        request.setAmount(amount);
        request.setCounterparty(counterparty);
        return ResponseEntity.ok(projectService.submitProjectExpense(projectId, request, invoiceFiles));
    }

    @PostMapping("/expenses/company")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> submitCompanyExpense(
            @RequestParam("expenseType") String expenseType,
            @RequestParam("itemName") String itemName,
            @RequestParam("amount") String amount,
            @RequestParam(value = "counterparty", required = false) String counterparty,
            @RequestParam(value = "invoiceFiles", required = false) List<MultipartFile> invoiceFiles) {
        SubmitProjectExpenseRequest request = new SubmitProjectExpenseRequest();
        request.setExpenseType(expenseType);
        request.setItemName(itemName);
        request.setAmount(amount);
        request.setCounterparty(counterparty);
        return ResponseEntity.ok(projectService.submitProjectExpense(
                CompanyProjectInitializer.COMPANY_PROJECT_ID, request, invoiceFiles));
    }

    @PostMapping("/expenses/{expenseId}/review")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> reviewExpense(
            @PathVariable Long expenseId,
            @RequestBody ReviewExpenseRequest request) {
        return ResponseEntity.ok(projectService.reviewExpense(expenseId, request));
    }

    @GetMapping("/expenses/review-list")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getReviewList() {
        return ResponseEntity.ok(Map.of(
                "pending", projectService.getReviewableExpenses(),
                "history", projectService.getReviewedHistory()
        ));
    }

    @GetMapping("/expenses/{expenseId}/invoice")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> downloadExpenseInvoice(@PathVariable Long expenseId) {
        ProjectService.ExpenseInvoicePayload payload = projectService.loadExpenseInvoice(expenseId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (payload.contentType() != null && !payload.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(payload.contentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + payload.fileName() + "\"")
                .contentLength(payload.fileSize() == null ? payload.bytes().length : payload.fileSize())
                .body(new ByteArrayResource(payload.bytes()));
    }

    @GetMapping("/expenses/pending-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getPendingExpenseCount() {
        return ResponseEntity.ok(Map.of("count", projectService.getPendingExpenseCount()));
    }

    @GetMapping("/expenses/files/{fileId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> downloadExpenseFile(@PathVariable Long fileId) {
        ProjectService.ExpenseInvoicePayload payload = projectService.loadExpenseFile(fileId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (payload.contentType() != null && !payload.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(payload.contentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + payload.fileName() + "\"")
                .contentLength(payload.fileSize() == null ? payload.bytes().length : payload.fileSize())
                .body(new ByteArrayResource(payload.bytes()));
    }

    @GetMapping("/expenses/reimbursement-template")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> downloadReimbursementTemplate() {
        byte[] bytes = reimbursementZipService.generateTemplateExcel();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reimbursement_template.xlsx\"")
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/cost-adjustments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProjectCostAdjustment>> getCostAdjustmentLog() {
        return ResponseEntity.ok(projectService.getCostAdjustmentLog());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteProject(@PathVariable String id,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        projectService.deleteProject(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
