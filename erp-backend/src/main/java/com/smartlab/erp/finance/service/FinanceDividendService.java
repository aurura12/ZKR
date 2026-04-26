package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectStatus;
import com.smartlab.erp.finance.dto.*;
import com.smartlab.erp.finance.entity.*;
import com.smartlab.erp.finance.enums.FinanceCashFlowDirection;
import com.smartlab.erp.finance.enums.FinanceClearingStatus;
import com.smartlab.erp.finance.enums.FinanceDividendStatus;
import com.smartlab.erp.finance.enums.FinanceWalletTransactionType;
import com.smartlab.erp.finance.repository.*;
import com.smartlab.erp.finance.support.FinanceAmounts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinanceDividendService {

    private final FinanceReferenceService referenceService;
    private final FinanceClearingSheetRepository clearingSheetRepository;
    private final FinanceDividendSheetRepository dividendSheetRepository;
    private final FinanceVentureEquityRepository ventureEquityRepository;
    private final FinanceVentureProfileRepository ventureProfileRepository;
    private final FinanceWalletAccountRepository walletAccountRepository;
    private final FinanceWalletTransactionRepository walletTransactionRepository;
    private final FinanceBankBalanceSnapshotRepository bankBalanceSnapshotRepository;

    @Transactional
    public FinanceDividendPrepareResponse prepare(FinanceDividendPrepareRequest request) {
        SysProject project = referenceService.getRequiredProject(request.getProjectId());
        validateProjectSettlementStage(project);
        FinanceVentureProfile profile = getRequiredProfile(project.getProjectId());
        FinanceClearingSheet clearingSheet = getEligibleClearingSheet(project.getProjectId());

        if (dividendSheetRepository.existsByProject_ProjectIdAndStatus(project.getProjectId(), FinanceDividendStatus.PENDING)) {
            throw new IllegalArgumentException("Pending dividend sheets already exist for venture: " + project.getProjectId());
        }

        List<FinanceVentureEquity> equities = ventureEquityRepository.findByProject_ProjectIdAndActiveTrue(project.getProjectId());
        if (equities.isEmpty()) {
            throw new IllegalArgumentException("No active dividend roster found for project: " + project.getProjectId());
        }

        BigDecimal netProfit = FinanceAmounts.scale(clearingSheet.getNetProfit());
        BigDecimal ratioTotal = resolveRatioTotal(equities);
        BigDecimal allocated = BigDecimal.ZERO;
        List<FinanceDividendSheet> sheets = new ArrayList<>();

        for (int i = 0; i < equities.size(); i++) {
            FinanceVentureEquity equity = equities.get(i);
            BigDecimal ratio = resolveRatio(equity);
            BigDecimal amount = (i == equities.size() - 1)
                    ? FinanceAmounts.subtract(netProfit, allocated)
                    : FinanceAmounts.scale(netProfit.multiply(ratio).divide(ratioTotal, 2, RoundingMode.HALF_UP));
            allocated = FinanceAmounts.add(allocated, amount);
            sheets.add(FinanceDividendSheet.builder()
                    .project(project)
                    .user(equity.getUser())
                    .ledgerMonth(clearingSheet.getLedgerMonth())
                    .amount(amount)
                    .dividendRatio(ratio)
                    .netProfitSnapshot(netProfit)
                    .status(FinanceDividendStatus.PENDING)
                    .build());
        }

        List<FinanceDividendSheet> savedSheets = dividendSheetRepository.saveAll(sheets);
        return FinanceDividendPrepareResponse.builder()
                .venture(toVentureRef(profile))
                .ledgerMonth(clearingSheet.getLedgerMonth())
                .netProfit(netProfit)
                .totalAmount(netProfit)
                .items(savedSheets.stream().map(sheet -> toDividendItemView(sheet, profile)).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public FinanceDividendListResponse list(String projectId, FinanceDividendStatus status) {
        List<FinanceDividendSheet> sheets;
        if (projectId != null && !projectId.isBlank() && status != null) {
            sheets = dividendSheetRepository.findByProject_ProjectIdAndStatus(projectId, status);
        } else if (status != null) {
            sheets = dividendSheetRepository.findByStatus(status);
        } else if (projectId != null && !projectId.isBlank()) {
            sheets = dividendSheetRepository.findByProject_ProjectIdOrderByIdDesc(projectId);
        } else {
            sheets = dividendSheetRepository.findAll();
        }

        List<FinanceDividendItemView> items = sheets.stream()
                .map(sheet -> toDividendItemView(sheet, getRequiredProfile(sheet.getProject().getProjectId())))
                .toList();
        BigDecimal totalAmount = items.stream()
                .map(FinanceDividendItemView::getAmount)
                .reduce(BigDecimal.ZERO, FinanceAmounts::add);

        return FinanceDividendListResponse.builder()
                .items(items)
                .totalCount(items.size())
                .totalAmount(totalAmount)
                .build();
    }

    @Transactional
    public FinanceDividendConfirmResponse confirm(FinanceDividendConfirmRequest request, String fallbackOperator) {
        String operator = resolveOperator(request.getOperator(), fallbackOperator);
        List<FinanceDividendSheet> pendingSheets = dividendSheetRepository.findByProject_ProjectIdAndStatus(
                request.getProjectId(), FinanceDividendStatus.PENDING);
        if (pendingSheets.isEmpty()) {
            return buildConfirmedResponse(request.getProjectId());
        }

        FinanceVentureProfile profile = getRequiredProfile(request.getProjectId());
        BigDecimal totalAmount = pendingSheets.stream()
                .map(FinanceDividendSheet::getAmount)
                .reduce(BigDecimal.ZERO, FinanceAmounts::add);
        FinanceBankBalanceSnapshot latestSnapshot = bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()
                .orElseThrow(() -> new IllegalArgumentException("Bank balance snapshot is required before confirming dividends"));
        BigDecimal balanceBefore = FinanceAmounts.scale(latestSnapshot.getBalance());
        if (balanceBefore.compareTo(totalAmount) < 0) {
            throw new IllegalArgumentException("Pending dividend total " + totalAmount
                    + " exceeds latest bank balance " + balanceBefore);
        }

        List<FinanceMutationResult> walletResults = new ArrayList<>();
        for (FinanceDividendSheet sheet : pendingSheets) {
            FinanceWalletAccount wallet = referenceService.getOrCreateWallet(sheet.getUser().getUserId());
            BigDecimal newBalance = FinanceAmounts.add(wallet.getBalance(), sheet.getAmount());
            wallet.setBalance(newBalance);
            wallet.setTotalDividendEarned(FinanceAmounts.add(wallet.getTotalDividendEarned(), sheet.getAmount()));
            walletAccountRepository.save(wallet);

            FinanceWalletTransaction transaction = FinanceWalletTransaction.builder()
                    .wallet(wallet)
                    .transactionType(FinanceWalletTransactionType.DIVIDEND)
                    .cashFlowDirection(FinanceCashFlowDirection.IN)
                    .amount(FinanceAmounts.scale(sheet.getAmount()))
                    .balanceAfter(newBalance)
                    .project(sheet.getProject())
                    .sourceTable("finance_dividend_sheet")
                    .sourceId(sheet.getId())
                    .remark("Dividend confirmed for ledger month " + sheet.getLedgerMonth())
                    .build();
            walletTransactionRepository.save(transaction);

            sheet.setStatus(FinanceDividendStatus.CONFIRMED);
            sheet.setConfirmedAt(Instant.now());
            sheet.setConfirmedBy(operator);
            walletResults.add(FinanceMutationResult.builder()
                    .id(String.valueOf(wallet.getId()))
                    .message("Wallet posted for user " + sheet.getUser().getUserId())
                    .build());
        }

        dividendSheetRepository.saveAll(pendingSheets);
        BigDecimal balanceAfter = FinanceAmounts.subtract(balanceBefore, totalAmount);
        bankBalanceSnapshotRepository.save(FinanceBankBalanceSnapshot.builder()
                .balance(balanceAfter)
                .operator(operator)
                .remark(request.getRemark() == null || request.getRemark().isBlank()
                        ? "Dividend confirmation payout"
                        : request.getRemark())
                .snapshotAt(Instant.now())
                .build());

        return FinanceDividendConfirmResponse.builder()
                .venture(toVentureRef(profile))
                .ledgerMonth(pendingSheets.get(0).getLedgerMonth())
                .confirmedCount(pendingSheets.size())
                .totalAmount(totalAmount)
                .bankBalanceBefore(balanceBefore)
                .bankBalanceAfter(balanceAfter)
                .walletResults(walletResults)
                .build();
    }

    private FinanceDividendConfirmResponse buildConfirmedResponse(String projectId) {
        List<FinanceDividendSheet> confirmedSheets = dividendSheetRepository
                .findByProject_ProjectIdAndStatusOrderByConfirmedAtDescIdDesc(projectId, FinanceDividendStatus.CONFIRMED);
        if (confirmedSheets.isEmpty() || confirmedSheets.get(0).getConfirmedAt() == null) {
            throw new IllegalArgumentException("No pending dividend sheets found for project: " + projectId);
        }

        Instant latestConfirmedAt = confirmedSheets.get(0).getConfirmedAt();
        List<FinanceDividendSheet> latestBatch = confirmedSheets.stream()
                .filter(sheet -> latestConfirmedAt.equals(sheet.getConfirmedAt()))
                .toList();

        FinanceVentureProfile profile = getRequiredProfile(projectId);
        BigDecimal totalAmount = latestBatch.stream()
                .map(FinanceDividendSheet::getAmount)
                .reduce(BigDecimal.ZERO, FinanceAmounts::add);
        FinanceBankBalanceSnapshot latestSnapshot = bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()
                .orElseThrow(() -> new IllegalArgumentException("Bank balance snapshot is required before confirming dividends"));
        BigDecimal balanceAfter = FinanceAmounts.scale(latestSnapshot.getBalance());

        return FinanceDividendConfirmResponse.builder()
                .venture(toVentureRef(profile))
                .ledgerMonth(latestBatch.get(0).getLedgerMonth())
                .confirmedCount(latestBatch.size())
                .totalAmount(totalAmount)
                .bankBalanceBefore(FinanceAmounts.add(balanceAfter, totalAmount))
                .bankBalanceAfter(balanceAfter)
                .walletResults(List.of())
                .build();
    }

    private FinanceClearingSheet getEligibleClearingSheet(String projectId) {
        FinanceClearingSheet clearingSheet = clearingSheetRepository.findTopByProject_ProjectIdOrderByLedgerMonthDescIdDesc(projectId)
                .orElseThrow(() -> new IllegalArgumentException("No clearing sheet found for project: " + projectId));
        if (clearingSheet.getStatus() != FinanceClearingStatus.CLEARED) {
            throw new IllegalArgumentException("Project must be cleared before preparing dividends");
        }
        if (FinanceAmounts.scale(clearingSheet.getNetProfit()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Project net profit must be positive before preparing dividends");
        }
        return clearingSheet;
    }

    private FinanceVentureProfile getRequiredProfile(String projectId) {
        return ventureProfileRepository.findByProject_ProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Finance venture profile not found for project: " + projectId));
    }

    private void validateProjectSettlementStage(SysProject project) {
        if (project.getFlowType() != FlowType.PROJECT) {
            throw new IllegalArgumentException("Only project flow in settlement can trigger dividends");
        }
        if (project.getProjectStatus() != ProjectStatus.SETTLEMENT && project.getProjectStatus() != ProjectStatus.COMPLETED) {
            throw new IllegalArgumentException("Project must enter settlement before preparing dividends");
        }
    }

    private BigDecimal resolveRatioTotal(List<FinanceVentureEquity> equities) {
        BigDecimal ratioTotal = equities.stream()
                .map(this::resolveRatio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ratioTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Dividend ratios must add up to a positive value");
        }
        return ratioTotal;
    }

    private BigDecimal resolveRatio(FinanceVentureEquity equity) {
        BigDecimal ratio = Optional.ofNullable(equity.getDividendRatio()).orElse(equity.getEquityRatio());
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Dividend ratio must be positive for user: " + equity.getUser().getUserId());
        }
        return ratio;
    }

    private FinanceDividendItemView toDividendItemView(FinanceDividendSheet sheet, FinanceVentureProfile profile) {
        return FinanceDividendItemView.builder()
                .dividendSheetId(sheet.getId())
                .venture(toVentureRef(profile))
                .user(toUserRef(sheet.getUser()))
                .ledgerMonth(sheet.getLedgerMonth())
                .amount(FinanceAmounts.scale(sheet.getAmount()))
                .dividendRatio(sheet.getDividendRatio())
                .netProfitSnapshot(FinanceAmounts.scale(sheet.getNetProfitSnapshot()))
                .status(sheet.getStatus())
                .confirmedAt(sheet.getConfirmedAt())
                .confirmedBy(sheet.getConfirmedBy())
                .build();
    }

    private FinanceVentureRef toVentureRef(FinanceVentureProfile profile) {
        return FinanceVentureRef.builder()
                .projectId(profile.getProject().getProjectId())
                .legacyVentureId(profile.getLegacyVentureId())
                .displayName(profile.getDisplayName())
                .legacyStage(profile.getLegacyStage())
                .build();
    }

    private FinanceUserRef toUserRef(User user) {
        return FinanceUserRef.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }

    private String resolveOperator(String requestOperator, String fallbackOperator) {
        if (requestOperator != null && !requestOperator.isBlank()) {
            return requestOperator;
        }
        if (fallbackOperator != null && !fallbackOperator.isBlank()) {
            return fallbackOperator;
        }
        throw new IllegalArgumentException("Operator is required for dividend confirmation");
    }
}
