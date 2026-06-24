package com.smartlab.erp.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceStatementsResponse {
    private String latestLedgerMonth;
    private Instant lastUpdatedAt;
    private List<KpiCard> kpis;
    private IncomeStatement incomeStatement;
    private BalanceSheet balanceSheet;
    private CashFlowStatement cashFlowStatement;
    private Reconciliation reconciliation;
    private List<RiskRow> riskRows;
    private List<TrendPoint> trend;
    private List<ActiveProjectAccounting> activeProjectAccounting;
    private List<IdleSubject> idleSubjects;
    private CompanyExpenseSummary companyExpenseSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpiCard {
        private String key;
        private String label;
        private BigDecimal value;
        private String unit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncomeStatement {
        private BigDecimal totalRevenue;
        private BigDecimal totalCost;
        private BigDecimal totalMiddlewareFee;
        private BigDecimal companyOperatingExpense;
        private BigDecimal totalProfit;
        private BigDecimal totalLoss;
        private BigDecimal profitRate;
        private BigDecimal lossRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceSheet {
        private BigDecimal bankBalance;
        private BigDecimal internalPayables;
        private BigDecimal activeProjectAssets;
        private BigDecimal activeProjectLiabilities;
        private BigDecimal companyExpensePayables;
        private BigDecimal totalAssets;
        private BigDecimal totalLiabilities;
        private BigDecimal netAssets;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveProjectAccounting {
        private String projectId;
        private String name;
        private String flowType;
        private String status;
        private String projectType;
        private String projectTier;
        private String description;
        private String managerId;
        private String managerName;
        private String primaryOwnerId;
        private String primaryOwnerName;
        private List<MemberInfo> members;
        private BigDecimal estimatedAsset;
        private BigDecimal estimatedLiability;
        private BigDecimal netPosition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private String userId;
        private String name;
        private String role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashFlowStatement {
        private BigDecimal totalIn;
        private BigDecimal totalOut;
        private BigDecimal netCashFlow;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reconciliation {
        private BigDecimal actualBankBalance;
        private BigDecimal theoreticalBalance;
        private BigDecimal adjustmentNet;
        private BigDecimal variance;
        private boolean matched;
        private boolean snapshotRecorded;
        private Instant snapshotAt;
        private String operator;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskRow {
        private FinanceVentureRef venture;
        private BigDecimal netProfit;
        private BigDecimal carryForwardLoss;
        private String riskView;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String ledgerMonth;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal middlewareFee;
        private BigDecimal netProfit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdleSubject {
        private String subjectKey;
        private String subjectLabel;
        private BigDecimal balance;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyExpenseSummary {
        private BigDecimal totalExpense;
        private Map<String, BigDecimal> expenseByCategory;
    }
}
