package com.smartlab.erp.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.entity.ContractLedger;
import com.smartlab.erp.entity.ProjectExpense;
import com.smartlab.erp.entity.ProjectExpenseFile;
import com.smartlab.erp.repository.ContractLedgerRepository;
import com.smartlab.erp.repository.ProjectExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractOcrService {

    private final ProjectExpenseRepository expenseRepository;
    private final ContractLedgerRepository contractLedgerRepository;
    private final OcrClient ocrClient;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void triggerOcr(Long expenseId) {
        log.info("========== [合同OCR] 开始处理, expenseId={} ==========", expenseId);

        Optional<ProjectExpense> opt = expenseRepository.findById(expenseId);
        if (opt.isEmpty()) {
            log.warn("[合同OCR] expenseId={} 不存在", expenseId);
            return;
        }

        ProjectExpense expense = opt.get();
        ContractLedger ledger = ContractLedger.builder()
                .expenseId(expenseId)
                .ocrStatus("RUNNING")
                .build();

        ledger = contractLedgerRepository.save(ledger);

        for (ProjectExpenseFile file : expense.getFiles()) {
            Path filePath = Paths.get(file.getFilePath());
            if (!Files.exists(filePath)) {
                log.warn("[合同OCR] 文件不存在: {}", filePath);
                continue;
            }

            try {
                byte[] fileBytes = Files.readAllBytes(filePath);
                ContractOcrResult result = ocrClient.recognizeContract(fileBytes, file.getFileName());

                ledger.setOcrAt(Instant.now());

                if (result.isSuccess()) {
                    applyResult(ledger, result);
                    ledger.setOcrStatus("DONE");
                    ledger.setOcrRawJson(safeToJson(result));
                    log.info("[合同OCR] expenseId={} 识别成功, contractNo={}", expenseId, ledger.getContractNo());
                } else {
                    ledger.setOcrStatus("FAILED");
                    ledger.setOcrRawJson("{\"error\":\"" + escapeJson(result.getError()) + "\"}");
                    log.warn("[合同OCR] expenseId={} 识别失败: {}", expenseId, result.getError());
                }

                contractLedgerRepository.save(ledger);
                break;

            } catch (IOException e) {
                log.error("[合同OCR] 读取文件失败: {}", e.getMessage());
                ledger.setOcrStatus("FAILED");
                ledger.setOcrAt(Instant.now());
                ledger.setOcrRawJson("{\"error\":\"read file failed\"}");
                contractLedgerRepository.save(ledger);
            }
        }

        if ("RUNNING".equals(ledger.getOcrStatus())) {
            ledger.setOcrStatus("FAILED");
            ledger.setOcrAt(Instant.now());
            ledger.setOcrRawJson("{\"error\":\"no files found\"}");
            contractLedgerRepository.save(ledger);
        }

        log.info("========== [合同OCR] expenseId={} 处理完成 ==========", expenseId);
    }

    private void applyResult(ContractLedger ledger, ContractOcrResult result) {
        if (result.getCompany() != null) ledger.setCompany(result.getCompany());
        if (result.getContractNo() != null) ledger.setContractNo(result.getContractNo());
        if (result.getSigningEntity() != null) ledger.setSigningEntity(result.getSigningEntity());
        if (result.getCounterparty() != null) ledger.setCounterparty(result.getCounterparty());
        if (result.getDescription() != null) ledger.setDescription(result.getDescription());
        if (result.getSignType() != null) ledger.setSignType(result.getSignType());
        if (result.getSignDate() != null) {
            ledger.setSignDate(result.getSignDate());
            ledger.setSignYear(result.getSignDate().getYear());
            ledger.setSignMonth(result.getSignDate().getMonthValue());
        }
        if (result.getContractAmount() != null) ledger.setContractAmount(result.getContractAmount());
        if (result.getCurrency() != null) ledger.setCurrency(result.getCurrency());
        if (result.getPaymentMethod() != null) ledger.setPaymentMethod(result.getPaymentMethod());
        if (result.getStartDate() != null) ledger.setStartDate(result.getStartDate());
        if (result.getEndDate() != null) ledger.setEndDate(result.getEndDate());
        if (result.getCollectionDate() != null) ledger.setCollectionDate(result.getCollectionDate());
        if (result.getStatus() != null) ledger.setStatus(result.getStatus());
        if (result.getCollectedAmount() != null) ledger.setCollectedAmount(result.getCollectedAmount());
        if (result.getUncollectedAmount() != null) ledger.setUncollectedAmount(result.getUncollectedAmount());
        if (result.getInvoiceStatus() != null) ledger.setInvoiceStatus(result.getInvoiceStatus());
        if (result.getInvoiceAmount() != null) ledger.setInvoiceAmount(result.getInvoiceAmount());
        if (result.getResponsiblePerson() != null) ledger.setResponsiblePerson(result.getResponsiblePerson());
        if (result.getArchiveNo() != null) ledger.setArchiveNo(result.getArchiveNo());
        if (result.getRemarks() != null) ledger.setRemarks(result.getRemarks());
        ledger.setOcrConfidence(result.getConfidence());
    }

    private String safeToJson(ContractOcrResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
