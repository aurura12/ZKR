package com.smartlab.erp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "invoice_ledger", indexes = {
        @Index(name = "idx_il_expense_id", columnList = "expense_id"),
        @Index(name = "idx_il_tracking_no", columnList = "tracking_no"),
        @Index(name = "idx_il_ocr_status", columnList = "ocr_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq_no")
    private Long seqNo;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "excel_row")
    private Integer excelRow;

    @Column(name = "image_file", length = 500)
    private String imageFile;

    @Column(name = "image_hash", length = 64)
    private String imageHash;

    @Column(length = 150)
    private String company;

    @Column(name = "company_code", length = 50)
    private String companyCode;

    private Integer year;

    private Integer month;

    @Column(name = "expense_date")
    private LocalDate expenseDate;

    @Column(name = "tracking_no", length = 100)
    private String trackingNo;

    @Column(name = "expense_nature", length = 100)
    private String expenseNature;

    @Column(name = "amount_ex_tax", precision = 15, scale = 2)
    private BigDecimal amountExTax;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "tax_rate", length = 20)
    private String taxRate;

    @Column(name = "amount_incl_tax", precision = 15, scale = 2)
    private BigDecimal amountInclTax;

    @Column(length = 500)
    private String summary;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "is_project_rel")
    @Builder.Default
    private Boolean isProjectRel = true;

    @Column(name = "project_name", length = 150)
    private String projectName;

    @Column(name = "project_support", length = 500)
    private String projectSupport;

    @Column(length = 200)
    private String counterparty;

    @Column(name = "contract_no", length = 100)
    private String contractNo;

    @Column(name = "data_source", length = 20)
    @Builder.Default
    private String dataSource = "EXCEL";

    @Column(name = "ocr_status", length = 20)
    @Builder.Default
    private String ocrStatus = "PENDING";

    @Column(name = "ocr_raw_json", columnDefinition = "TEXT")
    private String ocrRawJson;

    @Column(name = "ocr_confidence", precision = 5, scale = 4)
    private BigDecimal ocrConfidence;

    @Column(name = "ocr_at")
    private Instant ocrAt;

    @Column(name = "verified_status", length = 20)
    @Builder.Default
    private String verifiedStatus = "PENDING";

    @Column(name = "verified_by", length = 64)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
