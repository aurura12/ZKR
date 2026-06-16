package com.smartlab.erp.entity;

import com.smartlab.erp.enums.CompanyExpenseCategory;
import com.smartlab.erp.enums.CompanyExpenseStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "company_expense")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category", length = 30)
    private CompanyExpenseCategory expenseCategory;

    @Column(name = "ledger_account", length = 50)
    private String ledgerAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20, nullable = false)
    @Builder.Default
    private CompanyExpenseStatus approvalStatus = CompanyExpenseStatus.PENDING_JIAOMIAO;

    @Column(name = "submitter_id", length = 64)
    private String submitterId;

    @Column(name = "supplier_name", length = 150)
    private String supplierName;

    @Column(name = "invoice_file_path", length = 500)
    private String invoiceFilePath;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "occurred_at")
    private LocalDate occurredAt;

    @Column(name = "jiaomiao_action", length = 20)
    private String jiaomiaoAction;

    @Column(name = "jiaomiao_at")
    private Instant jiaomiaoAt;

    @Column(name = "finance_action", length = 20)
    private String financeAction;

    @Column(name = "finance_at")
    private Instant financeAt;

    @Column(name = "chenlei_action", length = 20)
    private String chenleiAction;

    @Column(name = "chenlei_at")
    private Instant chenleiAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
