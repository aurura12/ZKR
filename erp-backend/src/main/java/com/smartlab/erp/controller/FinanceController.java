package com.smartlab.erp.controller;

import com.smartlab.erp.dto.AdjustProjectCostRequest;
import com.smartlab.erp.finance.dto.*;
import com.smartlab.erp.finance.enums.FinanceCashFlowDirection;
import com.smartlab.erp.finance.enums.FinanceWalletTransactionType;
import com.smartlab.erp.finance.service.FinanceExpenseSubmissionService;
import com.smartlab.erp.finance.service.FinanceReportingService;
import com.smartlab.erp.entity.ProjectCostAdjustment;
import com.smartlab.erp.finance.entity.FinanceCostBatch;
import com.smartlab.erp.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceReportingService financeReportingService;
    private final FinanceExpenseSubmissionService financeExpenseSubmissionService;
    private final ProjectService projectService;

    @GetMapping("/statements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinanceApiResponse<FinanceStatementsResponse>> getStatements() {
        String traceId = traceId();
        FinanceStatementsResponse payload = financeReportingService.getStatements();
        return ResponseEntity.ok(FinanceApiResponse.success("finance statements loaded", payload, null, traceId));
    }

    @GetMapping("/wallets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinanceApiResponse<FinanceWalletOverviewResponse>> getWallets() {
        String traceId = traceId();
        FinanceWalletOverviewResponse payload = financeReportingService.getWalletOverview();
        return ResponseEntity.ok(FinanceApiResponse.success("finance wallets loaded", payload, null, traceId));
    }

    @GetMapping("/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinanceApiResponse<FinanceTransactionListResponse>> getTransactions(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) FinanceWalletTransactionType type,
            @RequestParam(required = false) FinanceCashFlowDirection direction,
            @RequestParam(required = false) String sourceTable) {
        String traceId = traceId();
        FinanceTransactionListResponse payload = financeReportingService.getTransactions(limit, userId, type, direction, sourceTable);
        FinanceResponseMeta meta = FinanceResponseMeta.builder()
                .page(1)
                .size(payload.getLimit())
                .total(payload.getTotalCount())
                .totalPages(payload.getTotalCount() == 0 ? 0 : 1)
                .build();
        return ResponseEntity.ok(FinanceApiResponse.success("finance transactions loaded", payload, meta, traceId));
    }

    @GetMapping("/submissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinanceApiResponse<FinanceExpenseSubmissionCenterResponse>> getExpenseSubmissions() {
        String traceId = traceId();
        FinanceExpenseSubmissionCenterResponse payload = financeExpenseSubmissionService.getSubmissionCenter();
        return ResponseEntity.ok(FinanceApiResponse.success("finance expense submissions loaded", payload, null, traceId));
    }

    @GetMapping("/submissions/{submissionId}/invoice")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> downloadExpenseInvoice(@PathVariable Long submissionId) {
        FinanceExpenseSubmissionService.InvoiceDownloadPayload payload = financeExpenseSubmissionService.loadInvoice(submissionId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (payload.contentType() != null && !payload.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(payload.contentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .contentLength(payload.fileSize() == null ? payload.bytes().length : payload.fileSize())
                .body(new ByteArrayResource(payload.bytes()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<FinanceApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String traceId = traceId();
        return ResponseEntity.badRequest().body(FinanceApiResponse.failure(ex.getMessage(), traceId));
    }

    @GetMapping("/cost-adjustments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProjectCostAdjustment>> getCostAdjustmentLog() {
        return ResponseEntity.ok(projectService.getCostAdjustmentLog());
    }

    @PostMapping("/projects/{projectId}/cost-adjustments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> submitCostAdjustment(
            @PathVariable String projectId,
            @RequestParam("itemName") String itemName,
            @RequestParam("type") String type,
            @RequestParam("amount") String amount,
            @RequestParam(value = "invoiceFile", required = false) MultipartFile invoiceFile) {
        AdjustProjectCostRequest request = new AdjustProjectCostRequest();
        request.setItemName(itemName);
        request.setType(type);
        request.setAmount(new BigDecimal(amount));
        projectService.adjustProjectCost(projectId, request, invoiceFile);
        return ResponseEntity.ok(Map.of("message", "成本调整已提交"));
    }

    @GetMapping("/batch-log")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FinanceCostBatch>> getBatchLog() {
        return ResponseEntity.ok(projectService.getBatchLog());
    }

    @GetMapping("/export/reimbursements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> exportReimbursements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] zipBytes = projectService.exportReimbursementsZip(from, to);
        String fileName = "报销汇总_" + from + "_" + to + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentLength(zipBytes.length)
                .body(new ByteArrayResource(zipBytes));
    }

    private String traceId() {
        return UUID.randomUUID().toString();
    }
}
