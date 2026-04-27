package com.smartlab.erp.entity;

import com.smartlab.erp.enums.ProjectCostAdjustmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "project_cost_adjustment", indexes = {
        @Index(name = "idx_pca_project_id", columnList = "project_id"),
        @Index(name = "idx_pca_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectCostAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "project_name", length = 150)
    private String projectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 30)
    private ProjectCostAdjustmentType adjustmentType;

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

    @Column(name = "operator_user_id", nullable = false, length = 64)
    private String operatorUserId;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
