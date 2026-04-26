package com.smartlab.erp.finance.entity;

import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "finance_cost_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_finance_cost_entry_project_user_day", columnNames = {"project_id", "user_id", "accrual_date"})
}, indexes = {
        @Index(name = "idx_finance_cost_entry_batch_project", columnList = "batch_id,project_id"),
        @Index(name = "idx_finance_cost_entry_month", columnList = "ledger_month")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinanceCostEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private FinanceCostBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private SysProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "ledger_month", nullable = false, length = 7)
    private String ledgerMonth;

    @Column(name = "accrual_date", nullable = false)
    private java.time.LocalDate accrualDate;

    @Column(name = "work_hours", precision = 10, scale = 2)
    private BigDecimal workHours;

    @Column(name = "daily_wage_snapshot", precision = 10, scale = 2)
    private BigDecimal dailyWageSnapshot;

    @Column(name = "labor_cost", precision = 15, scale = 2)
    private BigDecimal laborCost;

    @Column(name = "middleware_royalty_fee", precision = 15, scale = 2)
    private BigDecimal middlewareRoyaltyFee;

    @Column(name = "final_settlement_cost", precision = 15, scale = 2)
    private BigDecimal finalSettlementCost;

    @Column(name = "source_table", length = 100)
    private String sourceTable;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
