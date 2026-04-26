package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.MiddlewareAsset;
import com.smartlab.erp.entity.MiddlewareRoyaltyRoster;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.finance.dto.FinanceClearingExecuteRequest;
import com.smartlab.erp.finance.dto.FinanceClearingExecuteResponse;
import com.smartlab.erp.finance.dto.FinanceClearingRoyaltyItem;
import com.smartlab.erp.finance.dto.FinanceClearingVentureView;
import com.smartlab.erp.finance.dto.FinanceVentureRef;
import com.smartlab.erp.finance.entity.FinanceClearingSheet;
import com.smartlab.erp.finance.entity.FinanceCostSummary;
import com.smartlab.erp.finance.entity.FinanceMiddlewareUsage;
import com.smartlab.erp.finance.entity.FinanceVentureProfile;
import com.smartlab.erp.finance.entity.FinanceWalletAccount;
import com.smartlab.erp.finance.entity.FinanceWalletTransaction;
import com.smartlab.erp.finance.enums.FinanceCashFlowDirection;
import com.smartlab.erp.finance.enums.FinanceClearingStatus;
import com.smartlab.erp.finance.enums.FinanceWalletTransactionType;
import com.smartlab.erp.finance.repository.FinanceClearingSheetRepository;
import com.smartlab.erp.finance.repository.FinanceCostSummaryRepository;
import com.smartlab.erp.finance.repository.FinanceMiddlewareUsageRepository;
import com.smartlab.erp.finance.repository.FinanceVentureProfileRepository;
import com.smartlab.erp.finance.repository.FinanceWalletAccountRepository;
import com.smartlab.erp.finance.repository.FinanceWalletTransactionRepository;
import com.smartlab.erp.finance.support.FinanceAmounts;
import com.smartlab.erp.repository.MiddlewareRoyaltyRosterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FinanceClearingService {

    private static final BigDecimal ROYALTY_RATE = new BigDecimal("0.01");

    private final FinanceVentureProfileRepository ventureProfileRepository;
    private final FinanceCostSummaryRepository costSummaryRepository;
    private final FinanceClearingSheetRepository clearingSheetRepository;
    private final FinanceMiddlewareUsageRepository middlewareUsageRepository;
    private final FinanceWalletAccountRepository walletAccountRepository;
    private final FinanceWalletTransactionRepository walletTransactionRepository;
    private final FinanceReferenceService financeReferenceService;
    private final MiddlewareRoyaltyRosterRepository royaltyRosterRepository;

    @Transactional(readOnly = true)
    public List<FinanceClearingVentureView> listVentures() {
        return ventureProfileRepository.findByLedgerEnabledTrueOrderByLegacyVentureIdAsc().stream()
                .map(this::toVentureView)
                .toList();
    }

    @Transactional
    public FinanceClearingExecuteResponse execute(FinanceClearingExecuteRequest request) {
        if (request.hasUnsupportedFields()) {
            throw new IllegalArgumentException("Only venture_id and final_revenue are supported for clearing execution");
        }
        if (request.getFinalRevenue() == null || request.getFinalRevenue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("finalRevenue must be greater than or equal to 0");
        }

        FinanceVentureProfile venture = ventureProfileRepository.findByLegacyVentureId(request.getVentureId())
                .orElseThrow(() -> new IllegalArgumentException("Finance venture not found: " + request.getVentureId()));
        SysProject project = venture.getProject();

        FinanceCostSummary summary = resolveSummary(project.getProjectId());
        String ledgerMonth = summary.getLedgerMonth();
        FinanceClearingSheet existingSheet = clearingSheetRepository.findByProject_ProjectIdAndLedgerMonth(project.getProjectId(), ledgerMonth)
                .orElse(null);
        if (existingSheet != null && existingSheet.getStatus() == FinanceClearingStatus.CLEARED) {
            return toExecuteResponse(existingSheet, venture, Collections.emptyList());
        }

        BigDecimal finalRevenue = FinanceAmounts.scale(request.getFinalRevenue());
        List<FinanceMiddlewareUsage> pendingUsages = middlewareUsageRepository
                .findByCallerProject_ProjectIdAndLedgerMonthAndClearingSheetIsNull(project.getProjectId(), ledgerMonth);

        BigDecimal singleRoyaltyFee = FinanceAmounts.scale(finalRevenue.multiply(ROYALTY_RATE));
        BigDecimal totalMiddlewareFee = FinanceAmounts.scale(singleRoyaltyFee.multiply(BigDecimal.valueOf(pendingUsages.size())));
        BigDecimal rawProfit = FinanceAmounts.subtract(FinanceAmounts.subtract(finalRevenue, summary.getTotalSettlementCost()), totalMiddlewareFee);
        BigDecimal netProfit = rawProfit.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : FinanceAmounts.scale(rawProfit);
        BigDecimal carryForwardLoss = rawProfit.compareTo(BigDecimal.ZERO) < 0
                ? FinanceAmounts.scale(rawProfit.abs())
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        FinanceClearingSheet clearingSheet = existingSheet == null ? new FinanceClearingSheet() : existingSheet;
        clearingSheet.setProject(project);
        clearingSheet.setLedgerMonth(ledgerMonth);
        clearingSheet.setFinalRevenue(finalRevenue);
        clearingSheet.setTotalCost(FinanceAmounts.scale(summary.getTotalSettlementCost()));
        clearingSheet.setMiddlewareFee(totalMiddlewareFee);
        clearingSheet.setNetProfit(netProfit);
        clearingSheet.setCarryForwardLoss(carryForwardLoss);
        clearingSheet.setStatus(FinanceClearingStatus.CLEARED);
        clearingSheet.setClearedAt(Instant.now());
        clearingSheet.setClearedBy(null);
        clearingSheet = clearingSheetRepository.save(clearingSheet);

        List<FinanceClearingRoyaltyItem> royaltyItems = new ArrayList<>();
        for (FinanceMiddlewareUsage pendingUsage : pendingUsages) {
            MiddlewareAsset asset = pendingUsage.getMiddleware();
            if (asset == null) {
                throw new IllegalArgumentException("Middleware usage record is missing middleware asset");
            }

            pendingUsage.setRoyaltyFee(singleRoyaltyFee);
            pendingUsage.setClearingSheet(clearingSheet);
            if (pendingUsage.getSourceProject() == null) {
                pendingUsage.setSourceProject(financeReferenceService.getRequiredProject(asset.getSourceProjectId()));
            }
            middlewareUsageRepository.save(pendingUsage);
            royaltyItems.addAll(distributeRoyalty(project, clearingSheet, pendingUsage, singleRoyaltyFee));
        }

        return FinanceClearingExecuteResponse.builder()
                .clearingSheetId(clearingSheet.getId())
                .venture(toVentureRef(venture))
                .ledgerMonth(ledgerMonth)
                .finalRevenue(finalRevenue)
                .totalCost(FinanceAmounts.scale(summary.getTotalSettlementCost()))
                .middlewareFee(totalMiddlewareFee)
                .netProfit(netProfit)
                .lossTransferredToCompany(carryForwardLoss)
                .status(clearingSheet.getStatus())
                .clearedAt(clearingSheet.getClearedAt())
                .royaltyItems(royaltyItems)
                .build();
    }

    private FinanceCostSummary resolveSummary(String projectId) {
        return costSummaryRepository.findTopByProject_ProjectIdOrderByLedgerMonthDescIdDesc(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Cost batch summary not found for project: " + projectId));
    }

    private List<FinanceClearingRoyaltyItem> distributeRoyalty(SysProject callerProject,
                                                               FinanceClearingSheet clearingSheet,
                                                               FinanceMiddlewareUsage usage,
                                                               BigDecimal middlewareRoyaltyFee) {
        MiddlewareAsset asset = usage.getMiddleware();
        List<MiddlewareRoyaltyRoster> roster = royaltyRosterRepository.findByMiddleware_IdOrderByIdAsc(asset.getId());
        if (roster.isEmpty()) {
            SysProject sourceProject = usage.getSourceProject() != null
                    ? usage.getSourceProject()
                    : financeReferenceService.getRequiredProject(asset.getSourceProjectId());
            User fallbackUser = sourceProject.getManager();
            return List.of(postRoyalty(callerProject, clearingSheet, asset, fallbackUser, middlewareRoyaltyFee));
        }

        BigDecimal allocated = BigDecimal.ZERO;
        List<FinanceClearingRoyaltyItem> items = new ArrayList<>();
        for (int i = 0; i < roster.size(); i++) {
            MiddlewareRoyaltyRoster line = roster.get(i);
            BigDecimal amount;
            if (i == roster.size() - 1) {
                amount = FinanceAmounts.scale(middlewareRoyaltyFee.subtract(allocated));
            } else {
                amount = FinanceAmounts.scale(middlewareRoyaltyFee.multiply(line.getRoyaltyRatio()));
                allocated = FinanceAmounts.add(allocated, amount);
            }
            User user = financeReferenceService.getRequiredUser(line.getUserId());
            items.add(postRoyalty(callerProject, clearingSheet, asset, user, amount));
        }
        return items;
    }

    private FinanceClearingRoyaltyItem postRoyalty(SysProject callerProject,
                                                   FinanceClearingSheet clearingSheet,
                                                   MiddlewareAsset asset,
                                                   User user,
                                                   BigDecimal amount) {
        FinanceWalletAccount wallet = financeReferenceService.getOrCreateWallet(user.getUserId());
        BigDecimal nextBalance = FinanceAmounts.add(wallet.getBalance(), amount);
        wallet.setBalance(nextBalance);
        wallet.setTotalRoyaltyEarned(FinanceAmounts.add(wallet.getTotalRoyaltyEarned(), amount));
        wallet.setTotalMiddlewareProfit(FinanceAmounts.add(wallet.getTotalMiddlewareProfit(), amount));
        walletAccountRepository.save(wallet);

        walletTransactionRepository.save(FinanceWalletTransaction.builder()
                .wallet(wallet)
                .transactionType(FinanceWalletTransactionType.MIDDLEWARE_PROFIT)
                .cashFlowDirection(FinanceCashFlowDirection.IN)
                .amount(amount)
                .balanceAfter(nextBalance)
                .project(callerProject)
                .sourceTable("finance_clearing_sheet")
                .sourceId(clearingSheet.getId())
                .remark("Middleware royalty from " + asset.getName())
                .build());

        return FinanceClearingRoyaltyItem.builder()
                .middlewareId(asset.getId())
                .middlewareName(asset.getName())
                .userId(user.getUserId())
                .userName(user.getName())
                .amount(FinanceAmounts.scale(amount))
                .balanceAfter(nextBalance)
                .build();
    }

    private FinanceClearingVentureView toVentureView(FinanceVentureProfile venture) {
        String projectId = venture.getProject().getProjectId();
        FinanceCostSummary summary = costSummaryRepository.findTopByProject_ProjectIdOrderByLedgerMonthDescIdDesc(projectId).orElse(null);
        FinanceClearingSheet clearingSheet = clearingSheetRepository.findTopByProject_ProjectIdOrderByLedgerMonthDescIdDesc(projectId).orElse(null);
        boolean currentCycleCleared = summary == null
                ? clearingSheet != null
                : clearingSheet != null && Objects.equals(summary.getLedgerMonth(), clearingSheet.getLedgerMonth());
        return FinanceClearingVentureView.builder()
                .venture(toVentureRef(venture))
                .ledgerMonth(summary != null ? summary.getLedgerMonth() : clearingSheet == null ? null : clearingSheet.getLedgerMonth())
                .totalCost(summary == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : FinanceAmounts.scale(summary.getTotalSettlementCost()))
                .finalRevenue(currentCycleCleared ? FinanceAmounts.scale(clearingSheet.getFinalRevenue()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .middlewareFee(currentCycleCleared ? FinanceAmounts.scale(clearingSheet.getMiddlewareFee()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .netProfit(currentCycleCleared ? FinanceAmounts.scale(clearingSheet.getNetProfit()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .carryForwardLoss(currentCycleCleared ? FinanceAmounts.scale(clearingSheet.getCarryForwardLoss()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .status(currentCycleCleared ? clearingSheet.getStatus() : FinanceClearingStatus.PENDING)
                .clearedAt(currentCycleCleared ? clearingSheet.getClearedAt() : null)
                .costReady(summary != null)
                .build();
    }

    private FinanceClearingExecuteResponse toExecuteResponse(FinanceClearingSheet clearingSheet,
                                                             FinanceVentureProfile venture,
                                                             List<FinanceClearingRoyaltyItem> royaltyItems) {
        return FinanceClearingExecuteResponse.builder()
                .clearingSheetId(clearingSheet.getId())
                .venture(toVentureRef(venture))
                .ledgerMonth(clearingSheet.getLedgerMonth())
                .finalRevenue(FinanceAmounts.scale(clearingSheet.getFinalRevenue()))
                .totalCost(FinanceAmounts.scale(clearingSheet.getTotalCost()))
                .middlewareFee(FinanceAmounts.scale(clearingSheet.getMiddlewareFee()))
                .netProfit(FinanceAmounts.scale(clearingSheet.getNetProfit()))
                .lossTransferredToCompany(FinanceAmounts.scale(clearingSheet.getCarryForwardLoss()))
                .status(clearingSheet.getStatus())
                .clearedAt(clearingSheet.getClearedAt())
                .royaltyItems(royaltyItems)
                .build();
    }

    private FinanceVentureRef toVentureRef(FinanceVentureProfile venture) {
        return FinanceVentureRef.builder()
                .projectId(venture.getProject().getProjectId())
                .legacyVentureId(venture.getLegacyVentureId())
                .displayName(venture.getDisplayName())
                .legacyStage(venture.getLegacyStage())
                .build();
    }
}
