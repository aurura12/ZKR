package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectType;
import com.smartlab.erp.entity.ProjectStatus;
import com.smartlab.erp.entity.ProductStatus;
import com.smartlab.erp.entity.ResearchStatus;
import com.smartlab.erp.entity.ProductIdeaDetail;
import com.smartlab.erp.entity.ResearchProjectProfile;
import com.smartlab.erp.entity.CompanyExpense;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.enums.CompanyExpenseCategory;
import com.smartlab.erp.enums.CompanyExpenseStatus;
import com.smartlab.erp.finance.dto.*;
import com.smartlab.erp.finance.entity.*;
import com.smartlab.erp.finance.enums.FinanceAdjustmentDirection;
import com.smartlab.erp.finance.enums.FinanceCashFlowDirection;
import com.smartlab.erp.finance.enums.FinanceClearingStatus;
import com.smartlab.erp.finance.enums.FinanceWalletTransactionType;
import com.smartlab.erp.finance.repository.*;
import com.smartlab.erp.finance.support.FinanceAmounts;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.ProductIdeaDetailRepository;
import com.smartlab.erp.repository.ResearchProjectProfileRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.repository.CompanyExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceReportingService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FinanceClearingSheetRepository clearingSheetRepository;
    private final FinanceCostSummaryRepository costSummaryRepository;
    private final FinanceWalletAccountRepository walletAccountRepository;
    private final FinanceWalletTransactionRepository walletTransactionRepository;
    private final FinanceBankBalanceSnapshotRepository bankBalanceSnapshotRepository;
    private final FinanceAdjustmentLogRepository adjustmentLogRepository;
    private final FinanceVentureProfileRepository ventureProfileRepository;
    private final SysProjectRepository sysProjectRepository;
    private final SysProjectMemberRepository sysProjectMemberRepository;
    private final ProductIdeaDetailRepository productIdeaDetailRepository;
    private final ResearchProjectProfileRepository researchProjectProfileRepository;
    private final UserRepository userRepository;
    private final CompanyExpenseRepository companyExpenseRepository;

    @Transactional(readOnly = true)
    public FinanceStatementsResponse getStatements() {
        List<FinanceClearingSheet> clearingSheets = clearingSheetRepository.findAll();
        List<FinanceCostSummary> costSummaries = costSummaryRepository.findAll();
        List<FinanceWalletAccount> walletAccounts = walletAccountRepository.findAllByOwner_AccountDomain(AccountDomain.ERP);
        List<FinanceWalletTransaction> transactions = walletTransactionRepository.findAll().stream()
                .filter(this::isErpWalletTransaction)
                .toList();
        List<FinanceAdjustmentLog> adjustments = adjustmentLogRepository.findAll();
        Optional<FinanceBankBalanceSnapshot> latestSnapshot = bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc();
        Map<String, FinanceVentureProfile> ventureProfiles = ventureProfileRepository.findAll().stream()
                .collect(Collectors.toMap(profile -> profile.getProject().getProjectId(), Function.identity(), (left, right) -> right, HashMap::new));

        StatementTotals statementTotals = buildStatementTotals(clearingSheets, costSummaries, walletAccounts);
        CashPosition cashPosition = buildCashPosition(transactions, adjustments, latestSnapshot);
        BigDecimal totalWalletBalance = statementTotals.totalWalletBalance();
        List<FinanceStatementsResponse.ActiveProjectAccounting> activeProjectAccounting = buildActiveProjectAccounting(costSummaries);
        BigDecimal activeProjectAssets = sum(activeProjectAccounting, FinanceStatementsResponse.ActiveProjectAccounting::getEstimatedAsset);
        BigDecimal activeProjectLiabilities = sum(activeProjectAccounting, FinanceStatementsResponse.ActiveProjectAccounting::getEstimatedLiability);
        BigDecimal totalAssets = FinanceAmounts.add(cashPosition.actualBankBalance(), activeProjectAssets);
        List<FinanceStatementsResponse.IdleSubject> idleSubjects = buildIdleSubjects(
                cashPosition,
                walletAccounts,
                transactions,
                activeProjectAssets,
                activeProjectLiabilities
        );

        // --- Company Expense Summary ---
        List<CompanyExpense> approvedCompanyExpenses = companyExpenseRepository
                .findByApprovalStatusAndChenleiAtBetween(
                        CompanyExpenseStatus.APPROVED,
                        Instant.EPOCH,
                        Instant.now());
        BigDecimal totalCompanyExpense = approvedCompanyExpenses.stream()
                .map(CompanyExpense::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> companyExpenseByCategory = approvedCompanyExpenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getExpenseCategory() != null ? e.getExpenseCategory().name() : "OTHER",
                        Collectors.reducing(BigDecimal.ZERO, CompanyExpense::getTotalAmount, BigDecimal::add)));

        BigDecimal companyExpensePayables = FinanceAmounts.scale(totalCompanyExpense);
        BigDecimal totalLiabilities = FinanceAmounts.add(
                FinanceAmounts.add(totalWalletBalance, activeProjectLiabilities),
                companyExpensePayables);

        String latestLedgerMonth = resolveLatestLedgerMonth(clearingSheets, costSummaries);
        List<FinanceStatementsResponse.TrendPoint> trend = buildTrend(clearingSheets);
        List<FinanceStatementsResponse.RiskRow> riskRows = buildRiskRows(clearingSheets, ventureProfiles);

        return FinanceStatementsResponse.builder()
                .latestLedgerMonth(latestLedgerMonth)
                .lastUpdatedAt(Instant.now())
                .kpis(List.of(
                        kpi("total_profit", "Total Profit", statementTotals.totalProfit(), "CNY"),
                        kpi("total_loss", "Total Loss", statementTotals.totalLoss(), "CNY"),
                        kpi("total_middleware_fee", "Middleware Fee", statementTotals.totalMiddlewareFee(), "CNY"),
                        kpi("wallet_count", "Wallet Count", BigDecimal.valueOf(walletAccounts.size()), "COUNT"),
                        kpi("cleared_count", "Cleared Count", BigDecimal.valueOf(clearingSheets.stream().filter(sheet -> sheet.getStatus() == FinanceClearingStatus.CLEARED).count()), "COUNT"),
                        kpi("wallet_balance", "Wallet Balance", totalWalletBalance, "CNY"),
                        kpi("company_expense", "Company Expense", totalCompanyExpense, "CNY")
                ))
                .incomeStatement(FinanceStatementsResponse.IncomeStatement.builder()
                        .totalRevenue(statementTotals.totalRevenue())
                        .totalCost(statementTotals.totalCost())
                        .totalMiddlewareFee(statementTotals.totalMiddlewareFee())
                        .companyOperatingExpense(totalCompanyExpense)
                        .totalProfit(statementTotals.totalProfit())
                        .totalLoss(statementTotals.totalLoss())
                        .profitRate(rate(statementTotals.totalProfit(), statementTotals.totalRevenue()))
                        .lossRate(rate(statementTotals.totalLoss(), statementTotals.totalRevenue()))
                        .build())
                .balanceSheet(FinanceStatementsResponse.BalanceSheet.builder()
                        .bankBalance(cashPosition.actualBankBalance())
                        .internalPayables(totalWalletBalance)
                        .activeProjectAssets(activeProjectAssets)
                        .activeProjectLiabilities(activeProjectLiabilities)
                        .companyExpensePayables(companyExpensePayables)
                        .totalAssets(totalAssets)
                        .totalLiabilities(totalLiabilities)
                        .netAssets(FinanceAmounts.subtract(totalAssets, totalLiabilities))
                        .build())
                .cashFlowStatement(FinanceStatementsResponse.CashFlowStatement.builder()
                        .totalIn(cashPosition.totalIn())
                        .totalOut(cashPosition.totalOut())
                        .netCashFlow(cashPosition.netCashFlow())
                        .build())
                .reconciliation(buildReconciliation(cashPosition, latestSnapshot))
                .riskRows(riskRows)
                .trend(trend)
                .activeProjectAccounting(activeProjectAccounting)
                .idleSubjects(idleSubjects)
                .companyExpenseSummary(FinanceStatementsResponse.CompanyExpenseSummary.builder()
                        .totalExpense(totalCompanyExpense)
                        .expenseByCategory(companyExpenseByCategory)
                        .build())
                .build();
    }

    private List<FinanceStatementsResponse.ActiveProjectAccounting> buildActiveProjectAccounting(List<FinanceCostSummary> costSummaries) {
        List<SysProject> projects = sysProjectRepository.findAll().stream()
                .filter(this::isActiveProject)
                .sorted(Comparator.comparing(SysProject::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<String, BigDecimal> cumulativeCostByProject = costSummaries.stream()
                .filter(cs -> cs.getProject() != null && cs.getProject().getProjectId() != null)
                .collect(Collectors.groupingBy(
                        cs -> cs.getProject().getProjectId(),
                        Collectors.reducing(BigDecimal.ZERO,
                                FinanceCostSummary::getTotalSettlementCost,
                                BigDecimal::add)));

        List<String> projectIds = projects.stream().map(SysProject::getProjectId).filter(Objects::nonNull).toList();
        Map<String, List<SysProjectMember>> membersByProject = projectIds.isEmpty()
                ? Map.of()
                : sysProjectMemberRepository.findByProjectIdInWithUser(projectIds).stream()
                .collect(Collectors.groupingBy(SysProjectMember::getProjectId));
        Map<String, ProductIdeaDetail> ideaByProject = projectIds.isEmpty()
                ? Map.of()
                : productIdeaDetailRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(ProductIdeaDetail::getProjectId, item -> item, (left, right) -> right));
        Map<String, ResearchProjectProfile> researchByProject = projectIds.isEmpty()
                ? Map.of()
                : researchProjectProfileRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(ResearchProjectProfile::getProjectId, item -> item, (left, right) -> right));

        java.util.Set<String> userIds = new java.util.HashSet<>();
        projects.forEach(project -> {
            if (project.getManager() != null && project.getManager().getUserId() != null) {
                userIds.add(project.getManager().getUserId());
            }
            ProductIdeaDetail idea = ideaByProject.get(project.getProjectId());
            if (idea != null && idea.getIdeaOwnerUserId() != null && !idea.getIdeaOwnerUserId().isBlank()) {
                userIds.add(idea.getIdeaOwnerUserId());
            }
            ResearchProjectProfile profile = researchByProject.get(project.getProjectId());
            if (profile != null) {
                if (profile.getChiefEngineerUserId() != null && !profile.getChiefEngineerUserId().isBlank()) {
                    userIds.add(profile.getChiefEngineerUserId());
                }
                if (profile.getIdeaOwnerUserId() != null && !profile.getIdeaOwnerUserId().isBlank()) {
                    userIds.add(profile.getIdeaOwnerUserId());
                }
            }
        });
        Map<String, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getUserId, user -> user, (left, right) -> right));

        return projects.stream().map(project -> {
            BigDecimal estimatedAsset = scaleOrZero(project.getBudget());
            BigDecimal estimatedLiability = scaleOrZero(cumulativeCostByProject.getOrDefault(project.getProjectId(), BigDecimal.ZERO));
            List<FinanceStatementsResponse.MemberInfo> members = membersByProject.getOrDefault(project.getProjectId(), List.of()).stream()
                    .map(member -> FinanceStatementsResponse.MemberInfo.builder()
                            .userId(member.getUser().getUserId())
                            .name(resolveUserName(member.getUser()))
                            .role(member.getRole())
                            .build())
                    .toList();
            OwnerRef owner = resolveOwnerForFinanceStatement(
                    project,
                    members,
                    ideaByProject.get(project.getProjectId()),
                    researchByProject.get(project.getProjectId()),
                    userMap
            );
            return FinanceStatementsResponse.ActiveProjectAccounting.builder()
                    .projectId(project.getProjectId())
                    .name(project.getName())
                    .flowType(project.getFlowType() == null ? "UNKNOWN" : project.getFlowType().name())
                    .status(project.getCurrentStatus())
                    .projectType(resolveProjectType(project).name())
                    .projectTier(project.getProjectTier() == null ? "N" : project.getProjectTier().name())
                    .description(project.getDescription())
                    .managerId(owner.userId())
                    .managerName(owner.name())
                    .primaryOwnerId(owner.userId())
                    .primaryOwnerName(owner.name())
                    .members(members)
                    .estimatedAsset(estimatedAsset)
                    .estimatedLiability(estimatedLiability)
                    .netPosition(FinanceAmounts.subtract(estimatedAsset, estimatedLiability))
                    .build();
        }).toList();
    }

    private OwnerRef resolveOwnerForFinanceStatement(SysProject project,
                                                     List<FinanceStatementsResponse.MemberInfo> members,
                                                     ProductIdeaDetail ideaDetail,
                                                     ResearchProjectProfile researchProfile,
                                                     Map<String, User> userMap) {
        Map<String, String> memberNameMap = members.stream()
                .filter(member -> member.getUserId() != null && !member.getUserId().isBlank())
                .collect(Collectors.toMap(
                        FinanceStatementsResponse.MemberInfo::getUserId,
                        member -> member.getName() == null ? member.getUserId() : member.getName(),
                        (left, right) -> left
                ));
        String managerId = project.getManager() == null ? null : project.getManager().getUserId();
        String managerName = resolveUserName(project.getManager());

        String ownerId = null;
        if (project.getFlowType() == FlowType.PROJECT) {
            ownerId = managerId != null && !managerId.isBlank() ? managerId : findInitiatorId(members);
        } else if (project.getFlowType() == FlowType.PRODUCT) {
            ownerId = ideaDetail != null ? blankToNull(ideaDetail.getIdeaOwnerUserId()) : null;
            if (ownerId == null) ownerId = managerId != null && !managerId.isBlank() ? managerId : findInitiatorId(members);
        } else if (project.getFlowType() == FlowType.RESEARCH) {
            if (researchProfile != null) {
                ownerId = blankToNull(researchProfile.getChiefEngineerUserId());
                if (ownerId == null) ownerId = blankToNull(researchProfile.getIdeaOwnerUserId());
            }
            if (ownerId == null) ownerId = managerId != null && !managerId.isBlank() ? managerId : findInitiatorId(members);
        }

        if (ownerId == null) ownerId = managerId;

        String ownerName = ownerId == null ? null : memberNameMap.get(ownerId);
        if ((ownerName == null || ownerName.isBlank()) && ownerId != null) {
            ownerName = resolveUserName(userMap.get(ownerId));
        }
        if ((ownerName == null || ownerName.isBlank()) && ownerId != null && ownerId.equals(managerId)) {
            ownerName = managerName;
        }
        if (ownerName == null || ownerName.isBlank()) ownerName = "未指定负责人";
        return new OwnerRef(ownerId, ownerName);
    }

    private String findInitiatorId(List<FinanceStatementsResponse.MemberInfo> members) {
        return members.stream()
                .filter(member -> {
                    String role = String.valueOf(member.getRole() == null ? "" : member.getRole()).toUpperCase();
                    return role.equals("BUSINESS") || role.equals("BD") || role.equals("ADMIN") || role.equals("OWNER") || role.equals("INITIATOR");
                })
                .map(FinanceStatementsResponse.MemberInfo::getUserId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> members.stream().map(FinanceStatementsResponse.MemberInfo::getUserId).filter(Objects::nonNull).findFirst().orElse(null));
    }

    private String resolveUserName(User user) {
        if (user == null) return null;
        if (user.getName() != null && !user.getName().isBlank()) return user.getName();
        return user.getUsername();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private ProjectType resolveProjectType(SysProject project) {
        if (project.getProjectType() != null) {
            return project.getProjectType();
        }
        if (project.getFlowType() == FlowType.RESEARCH) {
            return ProjectType.AI_FOR_SCIENCE;
        }
        return ProjectType.BUSINESS;
    }

    private record OwnerRef(String userId, String name) {
    }

    private boolean isActiveProject(SysProject project) {
        if (project == null || project.getFlowType() == null) {
            return false;
        }
        if (project.getFlowType() == FlowType.PROJECT) {
            return project.getProjectStatus() != ProjectStatus.COMPLETED;
        }
        if (project.getFlowType() == FlowType.PRODUCT) {
            return project.getProductStatus() != ProductStatus.SHELVED;
        }
        if (project.getFlowType() == FlowType.RESEARCH) {
            return project.getResearchStatus() != ResearchStatus.ARCHIVE;
        }
        return false;
    }

    private StatementTotals buildStatementTotals(List<FinanceClearingSheet> clearingSheets,
                                                List<FinanceCostSummary> costSummaries,
                                                List<FinanceWalletAccount> walletAccounts) {
        BigDecimal totalRevenue = sum(clearingSheets, FinanceClearingSheet::getFinalRevenue);
        BigDecimal totalCost = resolveStatementAmount(
                clearingSheets,
                FinanceClearingSheet::getTotalCost,
                costSummaries,
                FinanceCostSummary::getTotalSettlementCost
        );
        BigDecimal totalMiddlewareFee = resolveStatementAmount(
                clearingSheets,
                FinanceClearingSheet::getMiddlewareFee,
                costSummaries,
                FinanceCostSummary::getTotalMiddlewareFee
        );
        BigDecimal totalProfit = sumPositive(clearingSheets, FinanceClearingSheet::getNetProfit);
        BigDecimal totalLoss = sumLoss(clearingSheets);
        BigDecimal totalWalletBalance = sum(walletAccounts, FinanceWalletAccount::getBalance);
        return new StatementTotals(totalRevenue, totalCost, totalMiddlewareFee, totalProfit, totalLoss, totalWalletBalance);
    }

    private BigDecimal resolveStatementAmount(List<FinanceClearingSheet> clearingSheets,
                                              Function<FinanceClearingSheet, BigDecimal> clearingExtractor,
                                              List<FinanceCostSummary> costSummaries,
                                              Function<FinanceCostSummary, BigDecimal> costSummaryExtractor) {
        BigDecimal amount = sum(clearingSheets, clearingExtractor);
        if (isZero(amount)) {
            return sum(costSummaries, costSummaryExtractor);
        }
        return amount;
    }

    private CashPosition buildCashPosition(List<FinanceWalletTransaction> transactions,
                                           List<FinanceAdjustmentLog> adjustments,
                                           Optional<FinanceBankBalanceSnapshot> latestSnapshot) {
        BigDecimal totalIn = sumFiltered(transactions, FinanceCashFlowDirection.IN);
        BigDecimal totalOut = sumFiltered(transactions, FinanceCashFlowDirection.OUT);
        BigDecimal netCashFlow = FinanceAmounts.subtract(totalIn, totalOut);
        BigDecimal adjustmentNet = sumAdjustmentNet(adjustments);
        BigDecimal theoreticalBalance = FinanceAmounts.add(netCashFlow, adjustmentNet);
        BigDecimal actualBankBalance = latestSnapshot.map(FinanceBankBalanceSnapshot::getBalance)
                .map(FinanceAmounts::scale)
                .orElse(BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
        BigDecimal variance = FinanceAmounts.subtract(actualBankBalance, theoreticalBalance);
        return new CashPosition(totalIn, totalOut, netCashFlow, adjustmentNet, theoreticalBalance, actualBankBalance, variance);
    }

    private FinanceStatementsResponse.Reconciliation buildReconciliation(CashPosition cashPosition,
                                                                         Optional<FinanceBankBalanceSnapshot> latestSnapshot) {
        return FinanceStatementsResponse.Reconciliation.builder()
                .actualBankBalance(cashPosition.actualBankBalance())
                .theoreticalBalance(cashPosition.theoreticalBalance())
                .adjustmentNet(cashPosition.adjustmentNet())
                .variance(cashPosition.variance())
                .matched(latestSnapshot.isPresent() && cashPosition.variance().compareTo(BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP)) == 0)
                .snapshotRecorded(latestSnapshot.isPresent())
                .snapshotAt(latestSnapshot.map(FinanceBankBalanceSnapshot::getSnapshotAt).orElse(null))
                .operator(latestSnapshot.map(FinanceBankBalanceSnapshot::getOperator).orElse(null))
                .remark(latestSnapshot.map(FinanceBankBalanceSnapshot::getRemark).orElse(null))
                .build();
    }

    private List<FinanceStatementsResponse.RiskRow> buildRiskRows(List<FinanceClearingSheet> clearingSheets,
                                                                  Map<String, FinanceVentureProfile> ventureProfiles) {
        return clearingSheets.stream()
                .filter(this::isLossRisk)
                .sorted(Comparator.comparing(FinanceClearingSheet::getLedgerMonth, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(FinanceClearingSheet::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(sheet -> FinanceStatementsResponse.RiskRow.builder()
                        .venture(toVentureRef(sheet.getProject(), ventureProfiles.get(sheet.getProject().getProjectId())))
                        .netProfit(FinanceAmounts.scale(sheet.getNetProfit()))
                        .carryForwardLoss(FinanceAmounts.scale(sheet.getCarryForwardLoss()))
                        .riskView("LOSS_RISK")
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public FinanceWalletOverviewResponse getWalletOverview() {
        List<FinanceWalletAccount> walletAccounts = walletAccountRepository.findAllByOwner_AccountDomain(AccountDomain.ERP).stream()
                .sorted(Comparator.comparing(FinanceWalletAccount::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return FinanceWalletOverviewResponse.builder()
                .summary(FinanceWalletOverviewResponse.Summary.builder()
                        .totalBalance(sum(walletAccounts, FinanceWalletAccount::getBalance))
                        .totalDividendEarned(sum(walletAccounts, FinanceWalletAccount::getTotalDividendEarned))
                        .totalRoyaltyEarned(sum(walletAccounts, FinanceWalletAccount::getTotalRoyaltyEarned))
                        .totalMiddlewareProfit(sum(walletAccounts, FinanceWalletAccount::getTotalMiddlewareProfit))
                        .totalPromotionExpense(sum(walletAccounts, FinanceWalletAccount::getTotalPromotionExpense))
                        .totalAdjustmentAmount(sum(walletAccounts, FinanceWalletAccount::getTotalAdjustmentAmount))
                        .walletCount(walletAccounts.size())
                        .build())
                .wallets(walletAccounts.stream()
                        .map(wallet -> FinanceWalletOverviewResponse.WalletRow.builder()
                                .walletId(wallet.getId())
                                .owner(toUserRef(wallet.getOwner()))
                                .role(wallet.getOwner() == null ? null : wallet.getOwner().getRole())
                                .balance(FinanceAmounts.scale(wallet.getBalance()))
                                .totalDividendEarned(FinanceAmounts.scale(wallet.getTotalDividendEarned()))
                                .totalRoyaltyEarned(FinanceAmounts.scale(wallet.getTotalRoyaltyEarned()))
                                .totalMiddlewareProfit(FinanceAmounts.scale(wallet.getTotalMiddlewareProfit()))
                                .totalPromotionExpense(FinanceAmounts.scale(wallet.getTotalPromotionExpense()))
                                .totalAdjustmentAmount(FinanceAmounts.scale(wallet.getTotalAdjustmentAmount()))
                                .createdAt(wallet.getCreatedAt())
                                .updatedAt(wallet.getUpdatedAt())
                                .build())
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public FinanceTransactionListResponse getTransactions(Integer limit,
                                                          String userId,
                                                          FinanceWalletTransactionType transactionType,
                                                          FinanceCashFlowDirection cashFlowDirection,
                                                          String sourceTable) {
        int resolvedLimit = limit == null || limit <= 0 ? 100 : Math.min(limit, 500);
        Map<String, FinanceVentureProfile> ventureProfiles = ventureProfileRepository.findAll().stream()
                .collect(Collectors.toMap(profile -> profile.getProject().getProjectId(), Function.identity(), (left, right) -> right, HashMap::new));

        List<FinanceTransactionListResponse.TransactionRow> filtered = walletTransactionRepository.findAll().stream()
                .filter(this::isErpWalletTransaction)
                .sorted(Comparator.comparing(FinanceWalletTransaction::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(FinanceWalletTransaction::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(tx -> userId == null || Objects.equals(tx.getWallet().getOwner().getUserId(), userId))
                .filter(tx -> transactionType == null || tx.getTransactionType() == transactionType)
                .filter(tx -> cashFlowDirection == null || tx.getCashFlowDirection() == cashFlowDirection)
                .filter(tx -> sourceTable == null || sourceTable.isBlank() || sourceTable.equalsIgnoreCase(tx.getSourceTable()))
                .map(tx -> FinanceTransactionListResponse.TransactionRow.builder()
                        .id(tx.getId())
                        .owner(toUserRef(tx.getWallet().getOwner()))
                        .venture(toVentureRef(tx.getProject(), tx.getProject() == null ? null : ventureProfiles.get(tx.getProject().getProjectId())))
                        .transactionType(tx.getTransactionType().name())
                        .cashFlowDirection(tx.getCashFlowDirection().name())
                        .amount(FinanceAmounts.scale(tx.getAmount()))
                        .balanceAfter(FinanceAmounts.scale(tx.getBalanceAfter()))
                        .sourceTable(tx.getSourceTable())
                        .sourceBusinessId(tx.getSourceId() == null ? null : String.valueOf(tx.getSourceId()))
                        .audit(FinanceAuditRef.builder().sourceTable(tx.getSourceTable()).sourceId(tx.getSourceId()).build())
                        .remark(tx.getRemark())
                        .createdAt(tx.getCreatedAt())
                        .build())
                .toList();

        List<FinanceTransactionListResponse.TransactionRow> limited = filtered.stream()
                .limit(resolvedLimit)
                .toList();

        return FinanceTransactionListResponse.builder()
                .limit(resolvedLimit)
                .totalCount((long) filtered.size())
                .items(limited)
                .build();
    }

    @Transactional
    public FinanceMutationResult recordBankBalance(FinanceBankBalanceRequest request) {
        FinanceBankBalanceSnapshot snapshot = bankBalanceSnapshotRepository.save(FinanceBankBalanceSnapshot.builder()
                .balance(FinanceAmounts.scale(request.getBalance()))
                .operator(request.getOperator().trim())
                .remark(request.getRemark())
                .snapshotAt(Instant.now())
                .build());

        return FinanceMutationResult.builder()
                .id(String.valueOf(snapshot.getId()))
                .message("bank balance snapshot recorded")
                .build();
    }

    private List<FinanceStatementsResponse.TrendPoint> buildTrend(List<FinanceClearingSheet> clearingSheets) {
        Map<String, List<FinanceClearingSheet>> grouped = clearingSheets.stream()
                .filter(sheet -> sheet.getLedgerMonth() != null)
                .collect(Collectors.groupingBy(FinanceClearingSheet::getLedgerMonth));

        List<String> months = new ArrayList<>(grouped.keySet());
        months.sort(Comparator.naturalOrder());
        return months.stream()
                .map(month -> {
                    List<FinanceClearingSheet> items = grouped.get(month);
                    return FinanceStatementsResponse.TrendPoint.builder()
                            .ledgerMonth(month)
                            .revenue(sum(items, FinanceClearingSheet::getFinalRevenue))
                            .cost(sum(items, FinanceClearingSheet::getTotalCost))
                            .middlewareFee(sum(items, FinanceClearingSheet::getMiddlewareFee))
                            .netProfit(sum(items, FinanceClearingSheet::getNetProfit))
                            .build();
                })
                .toList();
    }

    private String resolveLatestLedgerMonth(List<FinanceClearingSheet> clearingSheets, List<FinanceCostSummary> costSummaries) {
        return clearingSheets.stream()
                .map(FinanceClearingSheet::getLedgerMonth)
                .filter(Objects::nonNull)
                .max(String::compareTo)
                .or(() -> costSummaries.stream()
                        .map(FinanceCostSummary::getLedgerMonth)
                        .filter(Objects::nonNull)
                        .max(String::compareTo))
                .orElse(YearMonth.now(ZoneOffset.UTC).toString());
    }

    private List<FinanceStatementsResponse.IdleSubject> buildIdleSubjects(CashPosition cashPosition,
                                                                          List<FinanceWalletAccount> walletAccounts,
                                                                          List<FinanceWalletTransaction> transactions,
                                                                          BigDecimal activeProjectAssets,
                                                                          BigDecimal activeProjectLiabilities) {
        List<FinanceStatementsResponse.IdleSubject> idle = new ArrayList<>();
        if (isZero(cashPosition.actualBankBalance()) && transactions.isEmpty()) {
            idle.add(idleSubject("bank_balance", "银行存款", cashPosition.actualBankBalance(), "无银行快照且无资金流水"));
        }
        if (isZero(sum(walletAccounts, FinanceWalletAccount::getBalance)) && transactions.isEmpty()) {
            idle.add(idleSubject("internal_payables", "内部应付款(钱包)", BigDecimal.ZERO, "钱包余额和收支流水均为零"));
        }
        if (isZero(activeProjectAssets)) {
            idle.add(idleSubject("active_project_assets", "在研项目资产", activeProjectAssets, "无在研项目预算资产沉淀"));
        }
        if (isZero(activeProjectLiabilities)) {
            idle.add(idleSubject("active_project_liabilities", "在研项目负债", activeProjectLiabilities, "无在研项目成本负债沉淀"));
        }
        return idle;
    }

    private FinanceStatementsResponse.IdleSubject idleSubject(String key, String label, BigDecimal balance, String reason) {
        return FinanceStatementsResponse.IdleSubject.builder()
                .subjectKey(key)
                .subjectLabel(label)
                .balance(FinanceAmounts.scale(balance))
                .reason(reason)
                .build();
    }

    private boolean isLossRisk(FinanceClearingSheet sheet) {
        return scaleOrZero(sheet.getNetProfit()).compareTo(BigDecimal.ZERO) < 0
                || scaleOrZero(sheet.getCarryForwardLoss()).compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal sumLoss(List<FinanceClearingSheet> clearingSheets) {
        BigDecimal netLoss = clearingSheets.stream()
                .map(FinanceClearingSheet::getNetProfit)
                .filter(Objects::nonNull)
                .filter(value -> value.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal carryForwardLoss = sum(clearingSheets, FinanceClearingSheet::getCarryForwardLoss);
        return FinanceAmounts.add(netLoss, carryForwardLoss);
    }

    private BigDecimal sumPositive(List<FinanceClearingSheet> clearingSheets, Function<FinanceClearingSheet, BigDecimal> extractor) {
        return FinanceAmounts.scale(clearingSheets.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumAdjustmentNet(List<FinanceAdjustmentLog> adjustments) {
        BigDecimal total = adjustments.stream()
                .map(adjustment -> adjustment.getDirection() == FinanceAdjustmentDirection.DEBIT
                        ? scaleOrZero(adjustment.getAmount())
                        : scaleOrZero(adjustment.getAmount()).negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return FinanceAmounts.scale(total);
    }

    private BigDecimal sumFiltered(List<FinanceWalletTransaction> transactions, FinanceCashFlowDirection direction) {
        return FinanceAmounts.scale(transactions.stream()
                .filter(tx -> tx.getTransactionType() != FinanceWalletTransactionType.ADJUSTMENT)
                .filter(tx -> tx.getCashFlowDirection() == direction)
                .map(FinanceWalletTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> extractor) {
        return FinanceAmounts.scale(items.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return numerator.multiply(HUNDRED)
                .divide(denominator, 2, java.math.RoundingMode.HALF_UP);
    }

    private FinanceStatementsResponse.KpiCard kpi(String key, String label, BigDecimal value, String unit) {
        return FinanceStatementsResponse.KpiCard.builder()
                .key(key)
                .label(label)
                .value(FinanceAmounts.scale(value))
                .unit(unit)
                .build();
    }

    private FinanceUserRef toUserRef(User user) {
        if (user == null) {
            return null;
        }
        return FinanceUserRef.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }

    private FinanceVentureRef toVentureRef(SysProject project, FinanceVentureProfile profile) {
        if (project == null && profile == null) {
            return null;
        }
        return FinanceVentureRef.builder()
                .projectId(project != null ? project.getProjectId() : profile.getProject().getProjectId())
                .legacyVentureId(profile != null ? profile.getLegacyVentureId() : null)
                .displayName(profile != null ? profile.getDisplayName() : project.getName())
                .legacyStage(profile != null ? profile.getLegacyStage() : null)
                .build();
    }

    private BigDecimal scaleOrZero(BigDecimal value) {
        return FinanceAmounts.scale(value);
    }

    private boolean isZero(BigDecimal value) {
        return scaleOrZero(value).compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isErpWalletTransaction(FinanceWalletTransaction tx) {
        if (tx == null || tx.getWallet() == null || tx.getWallet().getOwner() == null) {
            return false;
        }
        return tx.getWallet().getOwner().getAccountDomain() == AccountDomain.ERP;
    }

    private record StatementTotals(BigDecimal totalRevenue,
                                   BigDecimal totalCost,
                                   BigDecimal totalMiddlewareFee,
                                   BigDecimal totalProfit,
                                   BigDecimal totalLoss,
                                   BigDecimal totalWalletBalance) {
    }

    private record CashPosition(BigDecimal totalIn,
                                BigDecimal totalOut,
                                BigDecimal netCashFlow,
                                BigDecimal adjustmentNet,
                                BigDecimal theoreticalBalance,
                                BigDecimal actualBankBalance,
                                BigDecimal variance) {
    }
}
