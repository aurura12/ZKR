package com.smartlab.erp.dto;

import com.smartlab.erp.service.ProjectFinancialMetricsService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberEarningsResponse {
    private String projectId;
    private String projectTier;
    private String tierLabel;
    private Boolean eligible;
    private String poolType;
    private String poolLabel;
    private BigDecimal estimatedRevenue;
    private BigDecimal humanCost;
    private BigDecimal remainingProfit;
    private BigDecimal poolRatio;
    private BigDecimal poolAmount;
    private BigDecimal shareRatio;
    private Integer responsibilityRatio;
    private Integer totalPoolResponsibility;
    private Integer participantCount;
    private Integer projectMemberCount;
    private BigDecimal predictedAmount;
    private Instant lastCostBatchAt;
    private String explanation;
    private ProjectFinancialMetricsService.CostBreakdown costBreakdown;
}
