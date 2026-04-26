package com.smartlab.erp.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceWalletOverviewResponse {
    private Summary summary;
    private List<WalletRow> wallets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private BigDecimal totalBalance;
        private BigDecimal totalDividendEarned;
        private BigDecimal totalRoyaltyEarned;
        private BigDecimal totalMiddlewareProfit;
        private BigDecimal totalPromotionExpense;
        private BigDecimal totalAdjustmentAmount;
        private long walletCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletRow {
        private Long walletId;
        private FinanceUserRef owner;
        private String role;
        private BigDecimal balance;
        private BigDecimal totalDividendEarned;
        private BigDecimal totalRoyaltyEarned;
        private BigDecimal totalMiddlewareProfit;
        private BigDecimal totalPromotionExpense;
        private BigDecimal totalAdjustmentAmount;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
