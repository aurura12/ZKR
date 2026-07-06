package com.smartlab.erp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "contract_ledger", indexes = {
        @Index(name = "idx_cl_expense_id", columnList = "expense_id"),
        @Index(name = "idx_cl_contract_no", columnList = "contract_no"),
        @Index(name = "idx_cl_ocr_status", columnList = "ocr_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(length = 150)
    private String company;

    @Column(name = "contract_no", length = 100)
    private String contractNo;

    @Column(name = "signing_entity", length = 200)
    private String signingEntity;

    @Column(length = 200)
    private String counterparty;

    @Column(length = 500)
    private String description;

    @Column(name = "sign_type", length = 50)
    private String signType;

    @Column(name = "sign_year")
    private Integer signYear;

    @Column(name = "sign_month")
    private Integer signMonth;

    @Column(name = "sign_date")
    private LocalDate signDate;

    @Column(name = "contract_amount", precision = 15, scale = 2)
    private BigDecimal contractAmount;

    @Column(length = 10)
    private String currency;

    @Column(name = "payment_method", length = 200)
    private String paymentMethod;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "collection_date")
    private LocalDate collectionDate;

    @Column(length = 50)
    private String status;

    @Column(name = "collected_amount", precision = 15, scale = 2)
    private BigDecimal collectedAmount;

    @Column(name = "uncollected_amount", precision = 15, scale = 2)
    private BigDecimal uncollectedAmount;

    @Column(name = "invoice_status", length = 100)
    private String invoiceStatus;

    @Column(name = "invoice_amount", precision = 15, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(name = "responsible_person", length = 100)
    private String responsiblePerson;

    @Column(name = "archive_no", length = 100)
    private String archiveNo;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "ocr_status", length = 20)
    @Builder.Default
    private String ocrStatus = "PENDING";

    @Column(name = "ocr_raw_json", columnDefinition = "TEXT")
    private String ocrRawJson;

    @Column(name = "ocr_confidence", precision = 5, scale = 4)
    private BigDecimal ocrConfidence;

    @Column(name = "ocr_at")
    private Instant ocrAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
