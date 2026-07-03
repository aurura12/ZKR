package com.smartlab.erp.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.entity.InvoiceLedger;
import com.smartlab.erp.repository.InvoiceLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceOcrService {

    private final InvoiceLedgerRepository invoiceLedgerRepository;
    private final OcrClient ocrClient;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void triggerOcr(Long expenseId) {
        log.info("========== [OCR] 开始处理报销 OCR, expenseId={} ==========", expenseId);

        List<InvoiceLedger> ledgers = invoiceLedgerRepository.findByExpenseIdOrderBySeqNo(expenseId);
        if (ledgers.isEmpty()) {
            log.info("[OCR] expenseId={} 无发票流水行，可能为非 ZIP 提交的报销，跳过 OCR", expenseId);
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (InvoiceLedger ledger : ledgers) {
            if (ledger.getImageFile() == null || ledger.getImageFile().isBlank()) {
                log.info("[OCR] seqNo={} 无关联发票图片，跳过", ledger.getSeqNo());
                continue;
            }

            Path imagePath = Paths.get(ledger.getImageFile());
            if (!Files.exists(imagePath)) {
                log.warn("[OCR] seqNo={} 图片文件不存在: {}", ledger.getSeqNo(), imagePath);
                ledger.setOcrStatus("FAILED");
                ledger.setOcrRawJson("{\"error\":\"image file not found\"}");
                invoiceLedgerRepository.save(ledger);
                failCount++;
                continue;
            }

            try {
                byte[] imageBytes = Files.readAllBytes(imagePath);
                String fileName = imagePath.getFileName().toString();

                log.info("[OCR] seqNo={} 开始识别: {}", ledger.getSeqNo(), fileName);
                OcrInvoiceResult result = ocrClient.recognizeInvoice(imageBytes, fileName);

                ledger.setOcrAt(Instant.now());

                if (result.isSuccess()) {
                    ledger.setOcrStatus("DONE");
                    ledger.setOcrConfidence(result.getConfidence());

                    if (result.getInvoiceNumber() != null) {
                        ledger.setInvoiceNumber(result.getInvoiceNumber());
                    }
                    if (result.getInvoiceDate() != null) {
                        ledger.setExpenseDate(result.getInvoiceDate());
                        ledger.setYear(result.getInvoiceDate().getYear());
                        ledger.setMonth(result.getInvoiceDate().getMonthValue());
                    }
                    if (result.getAmountInclTax() != null) {
                        ledger.setAmountInclTax(result.getAmountInclTax());
                    }
                    if (result.getAmountExTax() != null) {
                        ledger.setAmountExTax(result.getAmountExTax());
                    }
                    if (result.getTaxAmount() != null) {
                        ledger.setTaxAmount(result.getTaxAmount());
                    }
                    if (result.getTaxRate() != null) {
                        ledger.setTaxRate(result.getTaxRate());
                    }
                    if (result.getSellerName() != null) {
                        ledger.setCounterparty(result.getSellerName());
                    }
                    if (result.getBuyerName() != null) {
                        ledger.setCompany(result.getBuyerName());
                    }
                    if (result.getItems() != null) {
                        ledger.setSummary(result.getItems());
                    }

                    ledger.setDataSource("EXCEL+OCR");
                    ledger.setOcrRawJson(safeToJson(result));

                    crossValidate(ledger);

                    successCount++;
                    log.info("[OCR] seqNo={} 识别成功, confidence={}", ledger.getSeqNo(), result.getConfidence());
                } else {
                    ledger.setOcrStatus("FAILED");
                    ledger.setOcrRawJson("{\"error\":\"" + escapeJson(result.getError()) + "\"}");
                    failCount++;
                    log.warn("[OCR] seqNo={} 识别失败: {}", ledger.getSeqNo(), result.getError());
                }

                invoiceLedgerRepository.save(ledger);

            } catch (IOException e) {
                log.error("[OCR] seqNo={} 读取图片文件失败: {}", ledger.getSeqNo(), e.getMessage());
                ledger.setOcrStatus("FAILED");
                ledger.setOcrAt(Instant.now());
                ledger.setOcrRawJson("{\"error\":\"read file failed: " + escapeJson(e.getMessage()) + "\"}");
                invoiceLedgerRepository.save(ledger);
                failCount++;
            }
        }

        log.info("========== [OCR] expenseId={} 处理完成, 成功={}, 失败={} ==========", expenseId, successCount, failCount);
    }

    private void crossValidate(InvoiceLedger ledger) {
        if (ledger.getAmountInclTax() == null) {
            ledger.setVerifiedStatus("PENDING");
            return;
        }

        BigDecimal diff = BigDecimal.ZERO;
        int comparison = 0;

        if (ledger.getAmountExTax() != null && ledger.getTaxAmount() != null) {
            BigDecimal computed = ledger.getAmountExTax().add(ledger.getTaxAmount());
            diff = ledger.getAmountInclTax().subtract(computed).abs();
            comparison = diff.compareTo(new BigDecimal("0.01"));
        }

        if (comparison <= 0) {
            ledger.setVerifiedStatus("MATCH");
        } else {
            ledger.setVerifiedStatus("MISMATCH");
            log.warn("[OCR] seqNo={} 金额交叉校验不匹配: amountInclTax={}, amountExTax+taxAmount diff={}",
                    ledger.getSeqNo(), ledger.getAmountInclTax(), diff);
        }
    }

    private String safeToJson(OcrInvoiceResult result) {
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
