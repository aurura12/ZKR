package com.smartlab.erp.entity;

import com.smartlab.erp.enums.ProjectExpenseStatus;
import com.smartlab.erp.enums.ProjectExpenseType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "project_expense", indexes = {
        @Index(name = "idx_pe_project_id", columnList = "project_id"),
        @Index(name = "idx_pe_status", columnList = "status"),
        @Index(name = "idx_pe_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "project_name", length = 150)
    private String projectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 30)
    private ProjectExpenseType expenseType;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "invoice_file_name", length = 255)
    private String invoiceFileName;

    @Column(name = "invoice_file_path", length = 500)
    private String invoiceFilePath;

    @Column(name = "invoice_content_type", length = 255)
    private String invoiceContentType;

    @Column(name = "invoice_file_size")
    private Long invoiceFileSize;

    @Column(name = "submitter_user_id", nullable = false, length = 64)
    private String submitterUserId;

    @Column(name = "submitter_name", length = 100)
    private String submitterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectExpenseStatus status;

    @Column(name = "jiaomiao_action", length = 20)
    private String jiaomiaoAction;

    @Column(name = "jiaomiao_at")
    private Instant jiaomiaoAt;

    @Column(name = "chenlei_action", length = 20)
    private String chenleiAction;

    @Column(name = "chenlei_at")
    private Instant chenleiAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectExpenseFile> files = new ArrayList<>();

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
