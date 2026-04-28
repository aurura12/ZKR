package com.smartlab.erp.finance.controller;

import com.smartlab.erp.finance.dto.FinanceApiResponse;
import com.smartlab.erp.finance.dto.FinanceClearingExecuteRequest;
import com.smartlab.erp.finance.dto.FinanceLedgerMonthRequest;
import com.smartlab.erp.finance.service.FinanceClearingService;
import com.smartlab.erp.finance.service.FinanceCostBatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FinanceWorkbenchController {

    private final FinanceCostBatchService financeCostBatchService;
    private final FinanceClearingService financeClearingService;

    @PostMapping("/api/batch/run_cost")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinanceApiResponse<?>> runCostBatch(@Valid @RequestBody FinanceLedgerMonthRequest request) {
        return respond(traceId -> FinanceApiResponse.success(
                "cost batch completed",
                financeCostBatchService.runBatch(request.getLedgerMonth()),
                null,
                traceId), HttpStatus.OK);
    }

    @GetMapping("/api/batch/preview/{ventureId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinanceApiResponse<?>> previewCostBatch(@PathVariable Long ventureId,
                                                                  @RequestParam("ledgerMonth") String ledgerMonth) {
        return respond(traceId -> FinanceApiResponse.success(
                "cost preview loaded",
                financeCostBatchService.preview(ventureId, ledgerMonth),
                null,
                traceId), HttpStatus.OK);
    }

    @GetMapping("/api/clearing/ventures")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinanceApiResponse<?>> listClearingVentures() {
        return respond(traceId -> FinanceApiResponse.success(
                "clearing ventures loaded",
                financeClearingService.listVentures(),
                null,
                traceId), HttpStatus.OK);
    }

    @PostMapping("/api/clearing/execute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinanceApiResponse<?>> executeClearing(@Valid @RequestBody FinanceClearingExecuteRequest request) {
        if (request.hasUnsupportedFields()) {
            throw new IllegalArgumentException("Only venture_id and final_revenue are supported for clearing execution");
        }
        return respond(traceId -> FinanceApiResponse.success(
                "clearing completed",
                financeClearingService.execute(request),
                null,
                traceId), HttpStatus.OK);
    }

    private ResponseEntity<FinanceApiResponse<?>> respond(ResponseFactory factory, HttpStatus successStatus) {
        String traceId = UUID.randomUUID().toString();
        try {
            return ResponseEntity.status(successStatus).body(factory.build(traceId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(FinanceApiResponse.failure(ex.getMessage(), traceId));
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<FinanceApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(FinanceApiResponse.failure(resolveValidationMessage(ex), traceId()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<FinanceApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(FinanceApiResponse.failure(ex.getParameterName() + " is required", traceId()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<FinanceApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(FinanceApiResponse.failure(ex.getName() + " value is invalid", traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<FinanceApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(FinanceApiResponse.failure(ex.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<FinanceApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(FinanceApiResponse.failure("Request body contains unsupported or invalid fields", traceId()));
    }

    private String resolveValidationMessage(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Validation failed" : error.getDefaultMessage())
                .orElse("Validation failed");
    }

    private String traceId() {
        return UUID.randomUUID().toString();
    }

    @FunctionalInterface
    private interface ResponseFactory {
        FinanceApiResponse<?> build(String traceId);
    }
}
