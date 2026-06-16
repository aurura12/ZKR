package com.smartlab.erp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedProjectsSummaryResponse {
    private int activeProjectCount;
    private int pendingSubtaskCount;
    private int riskProjectCount;
    private BigDecimal managementRadius;
    private BigDecimal totalBudget;
    private BigDecimal totalCost;
    private BigDecimal totalHumanCost;
    private BigDecimal totalRemainingProfit;
    private BigDecimal profitMargin;
    private BigDecimal costUsageRate;
    private Instant lastCostBatchAt;

    @Builder.Default
    private List<ProjectCard> projects = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectCard {
        private String projectId;
        private String name;
        private String description;
        private String projectType;
        private String flowType;
        private String status;
        private BigDecimal budget;
        private BigDecimal cost;
        private BigDecimal remainingProfit;
        private int pendingSubtaskCount;
        private int delayedMemberCount;
        private boolean risk;
        private List<String> riskSignals;
        private String managerName;
        private String managerAvatar;
        private Instant createdAt;
        private String projectTier;
        private Boolean costControlEnabled;
    }
}
