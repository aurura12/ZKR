package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectType;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.finance.dto.FinanceStatementsResponse;
import com.smartlab.erp.finance.dto.FinanceTransactionListResponse;
import com.smartlab.erp.finance.dto.FinanceWalletOverviewResponse;
import com.smartlab.erp.finance.entity.FinanceAdjustmentLog;
import com.smartlab.erp.finance.entity.FinanceBankBalanceSnapshot;
import com.smartlab.erp.finance.entity.FinanceClearingSheet;
import com.smartlab.erp.finance.entity.FinanceCostSummary;
import com.smartlab.erp.finance.entity.FinanceVentureProfile;
import com.smartlab.erp.finance.entity.FinanceWalletAccount;
import com.smartlab.erp.finance.entity.FinanceWalletTransaction;
import com.smartlab.erp.finance.enums.FinanceAdjustmentDirection;
import com.smartlab.erp.finance.enums.FinanceCashFlowDirection;
import com.smartlab.erp.finance.enums.FinanceClearingStatus;
import com.smartlab.erp.finance.enums.FinanceWalletTransactionType;
import com.smartlab.erp.finance.repository.FinanceAdjustmentLogRepository;
import com.smartlab.erp.finance.repository.FinanceBankBalanceSnapshotRepository;
import com.smartlab.erp.finance.repository.FinanceClearingSheetRepository;
import com.smartlab.erp.finance.repository.FinanceCostSummaryRepository;
import com.smartlab.erp.finance.repository.FinanceVentureProfileRepository;
import com.smartlab.erp.finance.repository.FinanceWalletAccountRepository;
import com.smartlab.erp.finance.repository.FinanceWalletTransactionRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.ProductIdeaDetailRepository;
import com.smartlab.erp.repository.ResearchProjectProfileRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.repository.CompanyExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceReportingServiceTest {

    @Mock
    private FinanceClearingSheetRepository clearingSheetRepository;
    @Mock
    private FinanceCostSummaryRepository costSummaryRepository;
    @Mock
    private FinanceWalletAccountRepository walletAccountRepository;
    @Mock
    private FinanceWalletTransactionRepository walletTransactionRepository;
    @Mock
    private FinanceBankBalanceSnapshotRepository bankBalanceSnapshotRepository;
    @Mock
    private FinanceAdjustmentLogRepository adjustmentLogRepository;
    @Mock
    private FinanceVentureProfileRepository ventureProfileRepository;
    @Mock
    private SysProjectRepository sysProjectRepository;
    @Mock
    private SysProjectMemberRepository sysProjectMemberRepository;
    @Mock
    private ProductIdeaDetailRepository productIdeaDetailRepository;
    @Mock
    private ResearchProjectProfileRepository researchProjectProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyExpenseRepository companyExpenseRepository;

    private FinanceReportingService financeReportingService;

    @BeforeEach
    void setUp() {
        financeReportingService = new FinanceReportingService(
                clearingSheetRepository,
                costSummaryRepository,
                walletAccountRepository,
                walletTransactionRepository,
                bankBalanceSnapshotRepository,
                adjustmentLogRepository,
                ventureProfileRepository,
                sysProjectRepository,
                sysProjectMemberRepository,
                productIdeaDetailRepository,
                researchProjectProfileRepository,
                userRepository,
                companyExpenseRepository
        );
    }

    @Test
    void getStatementsBuildsStatementAggregatesAndReconciliationContract() {
        StatementFixture fixture = statementFixture();

        when(clearingSheetRepository.findAll()).thenReturn(List.of(fixture.profitSheet, fixture.lossSheet));
        when(costSummaryRepository.findAll()).thenReturn(List.of(FinanceCostSummary.builder().ledgerMonth("2026-03").build()));
        when(walletAccountRepository.findAll()).thenReturn(List.of(fixture.managerWallet, fixture.operatorWallet));
        when(walletTransactionRepository.findAll()).thenReturn(List.of(fixture.inTx, fixture.outTx, fixture.adjustmentTx));
        when(adjustmentLogRepository.findAll()).thenReturn(List.of(fixture.debitAdjustment, fixture.creditAdjustment));
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.of(fixture.snapshot));
        when(ventureProfileRepository.findAll()).thenReturn(List.of(fixture.healthyProfile, fixture.riskProfile));
        when(sysProjectRepository.findAll()).thenReturn(List.of(fixture.healthyVenture, fixture.riskVenture));

        FinanceStatementsResponse response = financeReportingService.getStatements();

        assertEquals(new BigDecimal("1600.00"), response.getIncomeStatement().getTotalRevenue());
        assertEquals(new BigDecimal("1350.00"), response.getIncomeStatement().getTotalCost());
        assertEquals(new BigDecimal("16.00"), response.getIncomeStatement().getTotalMiddlewareFee());
        assertEquals(new BigDecimal("340.00"), response.getIncomeStatement().getTotalProfit());
        assertEquals(new BigDecimal("126.00"), response.getIncomeStatement().getTotalLoss());
        assertEquals(new BigDecimal("21.25"), response.getIncomeStatement().getProfitRate());
        assertEquals(new BigDecimal("7.88"), response.getIncomeStatement().getLossRate());

        assertEquals(new BigDecimal("190.00"), response.getBalanceSheet().getBankBalance());
        assertEquals(new BigDecimal("210.00"), response.getBalanceSheet().getInternalPayables());
        assertEquals(new BigDecimal("-20.00"), response.getBalanceSheet().getNetAssets());

        assertEquals(new BigDecimal("300.00"), response.getCashFlowStatement().getTotalIn());
        assertEquals(new BigDecimal("140.00"), response.getCashFlowStatement().getTotalOut());
        assertEquals(new BigDecimal("160.00"), response.getCashFlowStatement().getNetCashFlow());

        assertNotNull(kpi(response, "wallet_balance"));
        assertEquals(new BigDecimal("210.00"), kpi(response, "wallet_balance").getValue());

        assertEquals(new BigDecimal("190.00"), response.getReconciliation().getActualBankBalance());
        assertEquals(new BigDecimal("180.00"), response.getReconciliation().getTheoreticalBalance());
        assertEquals(new BigDecimal("20.00"), response.getReconciliation().getAdjustmentNet());
        assertEquals(new BigDecimal("10.00"), response.getReconciliation().getVariance());
        assertFalse(response.getReconciliation().isMatched());
        assertTrue(response.getReconciliation().isSnapshotRecorded());
        assertEquals(fixture.snapshot.getSnapshotAt(), response.getReconciliation().getSnapshotAt());
        assertEquals("finance-bot", response.getReconciliation().getOperator());
        assertEquals("month close", response.getReconciliation().getRemark());

        assertEquals(1, response.getRiskRows().size());
        assertEquals("Risk Venture", response.getRiskRows().get(0).getVenture().getDisplayName());
        assertEquals(new BigDecimal("-106.00"), response.getRiskRows().get(0).getNetProfit());
        assertEquals(new BigDecimal("20.00"), response.getRiskRows().get(0).getCarryForwardLoss());
        assertEquals("LOSS_RISK", response.getRiskRows().get(0).getRiskView());
    }

    @Test
    void getStatementsMarksUnrecordedSnapshotWhenNoBankBalanceSnapshotExists() {
        StatementFixture fixture = statementFixture();

        when(clearingSheetRepository.findAll()).thenReturn(List.of(fixture.profitSheet, fixture.lossSheet));
        when(costSummaryRepository.findAll()).thenReturn(List.of(FinanceCostSummary.builder().ledgerMonth("2026-03").build()));
        when(walletAccountRepository.findAll()).thenReturn(List.of(fixture.managerWallet, fixture.operatorWallet));
        when(walletTransactionRepository.findAll()).thenReturn(List.of(fixture.inTx, fixture.outTx, fixture.adjustmentTx));
        when(adjustmentLogRepository.findAll()).thenReturn(List.of(fixture.debitAdjustment, fixture.creditAdjustment));
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.empty());
        when(ventureProfileRepository.findAll()).thenReturn(List.of(fixture.healthyProfile, fixture.riskProfile));
        when(sysProjectRepository.findAll()).thenReturn(List.of(fixture.healthyVenture, fixture.riskVenture));

        FinanceStatementsResponse response = financeReportingService.getStatements();

        assertEquals(new BigDecimal("0.00"), response.getReconciliation().getActualBankBalance());
        assertEquals(new BigDecimal("-180.00"), response.getReconciliation().getVariance());
        assertFalse(response.getReconciliation().isMatched());
        assertFalse(response.getReconciliation().isSnapshotRecorded());
        assertNull(response.getReconciliation().getSnapshotAt());
        assertNull(response.getReconciliation().getOperator());
        assertNull(response.getReconciliation().getRemark());
    }

    @Test
    void getStatementsMarksReconciliationMatchedWhenSnapshotEqualsTheoreticalBalance() {
        StatementFixture fixture = statementFixture();
        FinanceBankBalanceSnapshot exactSnapshot = FinanceBankBalanceSnapshot.builder()
                .id(100L)
                .balance(new BigDecimal("180.00"))
                .operator("auditor")
                .remark("exact match")
                .snapshotAt(Instant.parse("2026-03-12T09:00:00Z"))
                .build();

        when(clearingSheetRepository.findAll()).thenReturn(List.of(fixture.profitSheet, fixture.lossSheet));
        when(costSummaryRepository.findAll()).thenReturn(List.of(FinanceCostSummary.builder().ledgerMonth("2026-03").build()));
        when(walletAccountRepository.findAll()).thenReturn(List.of(fixture.managerWallet, fixture.operatorWallet));
        when(walletTransactionRepository.findAll()).thenReturn(List.of(fixture.inTx, fixture.outTx, fixture.adjustmentTx));
        when(adjustmentLogRepository.findAll()).thenReturn(List.of(fixture.debitAdjustment, fixture.creditAdjustment));
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.of(exactSnapshot));
        when(ventureProfileRepository.findAll()).thenReturn(List.of(fixture.healthyProfile, fixture.riskProfile));
        when(sysProjectRepository.findAll()).thenReturn(List.of(fixture.healthyVenture, fixture.riskVenture));

        FinanceStatementsResponse response = financeReportingService.getStatements();

        assertEquals(new BigDecimal("180.00"), response.getReconciliation().getActualBankBalance());
        assertEquals(new BigDecimal("180.00"), response.getReconciliation().getTheoreticalBalance());
        assertEquals(new BigDecimal("0.00"), response.getReconciliation().getVariance());
        assertTrue(response.getReconciliation().isMatched());
        assertTrue(response.getReconciliation().isSnapshotRecorded());
    }

    @Test
    void getStatementsFallsBackToCostSummaryTotalsWhenClearingSheetTotalsAreZero() {
        StatementFixture fixture = statementFixture();
        FinanceClearingSheet zeroCostSheet = FinanceClearingSheet.builder()
                .id(3L)
                .project(fixture.healthyVenture)
                .ledgerMonth("2026-03")
                .finalRevenue(new BigDecimal("500"))
                .totalCost(BigDecimal.ZERO)
                .middlewareFee(BigDecimal.ZERO)
                .netProfit(new BigDecimal("100"))
                .carryForwardLoss(BigDecimal.ZERO)
                .status(FinanceClearingStatus.CLEARED)
                .build();
        FinanceCostSummary fallbackSummary = FinanceCostSummary.builder()
                .project(fixture.healthyVenture)
                .ledgerMonth("2026-03")
                .totalSettlementCost(new BigDecimal("320"))
                .totalMiddlewareFee(new BigDecimal("12"))
                .build();

        when(clearingSheetRepository.findAll()).thenReturn(List.of(zeroCostSheet));
        when(costSummaryRepository.findAll()).thenReturn(List.of(fallbackSummary));
        when(walletAccountRepository.findAll()).thenReturn(List.of(fixture.managerWallet));
        when(walletTransactionRepository.findAll()).thenReturn(List.of(fixture.inTx));
        when(adjustmentLogRepository.findAll()).thenReturn(List.of());
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.empty());
        when(ventureProfileRepository.findAll()).thenReturn(List.of(fixture.healthyProfile));
        when(sysProjectRepository.findAll()).thenReturn(List.of(fixture.healthyVenture));

        FinanceStatementsResponse response = financeReportingService.getStatements();

        assertEquals(new BigDecimal("320.00"), response.getIncomeStatement().getTotalCost());
        assertEquals(new BigDecimal("12.00"), response.getIncomeStatement().getTotalMiddlewareFee());
    }

    @Test
    void getWalletOverviewReturnsCurrentBalanceAndCumulativeEarningsPerUser() {
        StatementFixture fixture = statementFixture();

        when(walletAccountRepository.findAll()).thenReturn(List.of(fixture.managerWallet, fixture.operatorWallet));

        FinanceWalletOverviewResponse response = financeReportingService.getWalletOverview();

        assertEquals(new BigDecimal("210.00"), response.getSummary().getTotalBalance());
        assertEquals(new BigDecimal("70.00"), response.getSummary().getTotalDividendEarned());
        assertEquals(new BigDecimal("30.00"), response.getSummary().getTotalRoyaltyEarned());
        assertEquals(2, response.getWallets().size());

        FinanceWalletOverviewResponse.WalletRow managerRow = response.getWallets().get(0);
        assertEquals(11L, managerRow.getWalletId());
        assertEquals("U-1", managerRow.getOwner().getUserId());
        assertEquals("BUSINESS", managerRow.getRole());
        assertEquals(new BigDecimal("120.00"), managerRow.getBalance());
        assertEquals(new BigDecimal("50.00"), managerRow.getTotalDividendEarned());
        assertEquals(new BigDecimal("20.00"), managerRow.getTotalRoyaltyEarned());
        assertEquals(new BigDecimal("0.00"), managerRow.getTotalAdjustmentAmount());
        assertEquals(Instant.parse("2026-03-11T10:15:30Z"), managerRow.getUpdatedAt());
    }

    @Test
    void getTransactionsReturnsAuditTraceabilityFieldsWithinTransactionReporting() {
        StatementFixture fixture = statementFixture();

        when(walletTransactionRepository.findAll()).thenReturn(List.of(fixture.outTx, fixture.inTx, fixture.adjustmentTx));
        when(ventureProfileRepository.findAll()).thenReturn(List.of(fixture.healthyProfile, fixture.riskProfile));

        FinanceTransactionListResponse response = financeReportingService.getTransactions(5, "U-1", FinanceWalletTransactionType.DIVIDEND, FinanceCashFlowDirection.IN, "finance_dividend_sheet");

        assertEquals(1L, response.getTotalCount());
        assertEquals(1, response.getItems().size());

        FinanceTransactionListResponse.TransactionRow row = response.getItems().get(0);
        assertEquals(101L, row.getId());
        assertNotNull(row.getOwner());
        assertEquals("U-1", row.getOwner().getUserId());
        assertEquals("manager", row.getOwner().getUsername());
        assertEquals("Manager", row.getOwner().getName());
        assertEquals("BUSINESS", row.getOwner().getRole());
        assertEquals("DIVIDEND", row.getTransactionType());
        assertEquals(new BigDecimal("300.00"), row.getAmount());
        assertNotNull(row.getAudit());
        assertEquals("finance_dividend_sheet", row.getAudit().getSourceTable());
        assertEquals(9001L, row.getAudit().getSourceId());
        assertEquals("finance_dividend_sheet", row.getSourceTable());
        assertEquals("9001", row.getSourceBusinessId());
        assertEquals("dividend posted", row.getRemark());
        assertEquals(Instant.parse("2026-03-11T09:30:00Z"), row.getCreatedAt());
    }

    private FinanceStatementsResponse.KpiCard kpi(FinanceStatementsResponse response, String key) {
        return response.getKpis().stream()
                .filter(card -> key.equals(card.getKey()))
                .findFirst()
                .orElse(null);
    }

    private User user(String id, String username, String name, String role) {
        return User.builder()
                .userId(id)
                .username(username)
                .password("secret")
                .name(name)
                .role(role)
                .build();
    }

    private SysProject project(String id, String name, User manager) {
        return SysProject.builder()
                .projectId(id)
                .name(name)
                .projectType(ProjectType.BUSINESS)
                .flowType(FlowType.PROJECT)
                .manager(manager)
                .build();
    }

    private StatementFixture statementFixture() {
        User manager = user("U-1", "manager", "Manager", "BUSINESS");
        SysProject healthyVenture = project("P-1", "Healthy Venture", manager);
        SysProject riskVenture = project("P-2", "Risk Venture", manager);

        FinanceClearingSheet profitSheet = FinanceClearingSheet.builder()
                .id(1L)
                .project(healthyVenture)
                .ledgerMonth("2026-02")
                .finalRevenue(new BigDecimal("1000"))
                .totalCost(new BigDecimal("650"))
                .middlewareFee(new BigDecimal("10"))
                .netProfit(new BigDecimal("340"))
                .carryForwardLoss(BigDecimal.ZERO)
                .status(FinanceClearingStatus.CLEARED)
                .build();
        FinanceClearingSheet lossSheet = FinanceClearingSheet.builder()
                .id(2L)
                .project(riskVenture)
                .ledgerMonth("2026-03")
                .finalRevenue(new BigDecimal("600"))
                .totalCost(new BigDecimal("700"))
                .middlewareFee(new BigDecimal("6"))
                .netProfit(new BigDecimal("-106"))
                .carryForwardLoss(new BigDecimal("20"))
                .status(FinanceClearingStatus.PENDING)
                .build();

        FinanceWalletAccount managerWallet = FinanceWalletAccount.builder()
                .id(11L)
                .owner(manager)
                .balance(new BigDecimal("120"))
                .totalDividendEarned(new BigDecimal("50"))
                .totalRoyaltyEarned(new BigDecimal("20"))
                .updatedAt(Instant.parse("2026-03-11T10:15:30Z"))
                .build();
        FinanceWalletAccount operatorWallet = FinanceWalletAccount.builder()
                .id(12L)
                .owner(user("U-2", "operator", "Operator", "FINANCE"))
                .balance(new BigDecimal("90"))
                .totalDividendEarned(new BigDecimal("20"))
                .totalRoyaltyEarned(new BigDecimal("10"))
                .updatedAt(Instant.parse("2026-03-10T10:15:30Z"))
                .build();

        FinanceWalletTransaction inTx = FinanceWalletTransaction.builder()
                .id(101L)
                .wallet(managerWallet)
                .project(healthyVenture)
                .transactionType(FinanceWalletTransactionType.DIVIDEND)
                .cashFlowDirection(FinanceCashFlowDirection.IN)
                .amount(new BigDecimal("300"))
                .balanceAfter(new BigDecimal("420"))
                .sourceTable("finance_dividend_sheet")
                .sourceId(9001L)
                .remark("dividend posted")
                .createdAt(Instant.parse("2026-03-11T09:30:00Z"))
                .build();
        FinanceWalletTransaction outTx = FinanceWalletTransaction.builder()
                .id(102L)
                .wallet(operatorWallet)
                .project(riskVenture)
                .transactionType(FinanceWalletTransactionType.WITHDRAWAL)
                .cashFlowDirection(FinanceCashFlowDirection.OUT)
                .amount(new BigDecimal("140"))
                .balanceAfter(new BigDecimal("90"))
                .sourceTable("finance_withdrawal_request")
                .sourceId(9002L)
                .remark("withdrawal approved")
                .createdAt(Instant.parse("2026-03-10T08:00:00Z"))
                .build();
        FinanceWalletTransaction adjustmentTx = FinanceWalletTransaction.builder()
                .id(103L)
                .wallet(managerWallet)
                .project(healthyVenture)
                .transactionType(FinanceWalletTransactionType.ADJUSTMENT)
                .cashFlowDirection(FinanceCashFlowDirection.IN)
                .amount(new BigDecimal("25"))
                .balanceAfter(new BigDecimal("145"))
                .sourceTable("finance_adjustment_log")
                .sourceId(9003L)
                .remark("manual adjustment")
                .createdAt(Instant.parse("2026-03-09T07:00:00Z"))
                .build();

        FinanceAdjustmentLog debitAdjustment = FinanceAdjustmentLog.builder()
                .direction(FinanceAdjustmentDirection.DEBIT)
                .amount(new BigDecimal("25"))
                .build();
        FinanceAdjustmentLog creditAdjustment = FinanceAdjustmentLog.builder()
                .direction(FinanceAdjustmentDirection.CREDIT)
                .amount(new BigDecimal("5"))
                .build();

        FinanceBankBalanceSnapshot snapshot = FinanceBankBalanceSnapshot.builder()
                .id(99L)
                .balance(new BigDecimal("190"))
                .operator("finance-bot")
                .remark("month close")
                .snapshotAt(Instant.parse("2026-03-11T12:00:00Z"))
                .build();

        FinanceVentureProfile healthyProfile = FinanceVentureProfile.builder()
                .project(healthyVenture)
                .legacyVentureId(1001L)
                .displayName("Healthy Venture")
                .legacyStage("ACTIVE")
                .build();
        FinanceVentureProfile riskProfile = FinanceVentureProfile.builder()
                .project(riskVenture)
                .legacyVentureId(1002L)
                .displayName("Risk Venture")
                .legacyStage("RISK")
                .build();

        return new StatementFixture(
                healthyVenture,
                riskVenture,
                profitSheet,
                lossSheet,
                managerWallet,
                operatorWallet,
                inTx,
                outTx,
                adjustmentTx,
                debitAdjustment,
                creditAdjustment,
                snapshot,
                healthyProfile,
                riskProfile
        );
    }

    private record StatementFixture(
            SysProject healthyVenture,
            SysProject riskVenture,
            FinanceClearingSheet profitSheet,
            FinanceClearingSheet lossSheet,
            FinanceWalletAccount managerWallet,
            FinanceWalletAccount operatorWallet,
            FinanceWalletTransaction inTx,
            FinanceWalletTransaction outTx,
            FinanceWalletTransaction adjustmentTx,
            FinanceAdjustmentLog debitAdjustment,
            FinanceAdjustmentLog creditAdjustment,
            FinanceBankBalanceSnapshot snapshot,
            FinanceVentureProfile healthyProfile,
            FinanceVentureProfile riskProfile) {
    }
}
