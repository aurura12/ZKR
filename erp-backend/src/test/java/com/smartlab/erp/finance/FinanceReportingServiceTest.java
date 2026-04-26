package com.smartlab.erp.finance;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectType;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.finance.dto.FinanceBankBalanceRequest;
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
import com.smartlab.erp.finance.service.FinanceReportingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
                userRepository
        );
    }

    @Test
    void buildsStatementsUsingClearingCashFlowAndAdjustmentSemantics() {
        User alice = user("U-1", "alice", "Alice", "BUSINESS");
        User bob = user("U-2", "bob", "Bob", "DEV");
        SysProject ventureA = project("P-1", "Venture A", alice);
        SysProject ventureB = project("P-2", "Venture B", alice);

        FinanceClearingSheet profitSheet = FinanceClearingSheet.builder()
                .id(1L)
                .project(ventureA)
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
                .project(ventureB)
                .ledgerMonth("2026-03")
                .finalRevenue(new BigDecimal("600"))
                .totalCost(new BigDecimal("700"))
                .middlewareFee(new BigDecimal("6"))
                .netProfit(new BigDecimal("-106"))
                .carryForwardLoss(new BigDecimal("20"))
                .status(FinanceClearingStatus.PENDING)
                .build();

        FinanceWalletAccount aliceWallet = FinanceWalletAccount.builder()
                .id(11L)
                .owner(alice)
                .balance(new BigDecimal("120"))
                .totalDividendEarned(new BigDecimal("80"))
                .totalRoyaltyEarned(new BigDecimal("20"))
                .totalAdjustmentAmount(new BigDecimal("5"))
                .updatedAt(Instant.parse("2026-03-11T10:15:30Z"))
                .build();
        FinanceWalletAccount bobWallet = FinanceWalletAccount.builder()
                .id(12L)
                .owner(bob)
                .balance(new BigDecimal("90"))
                .totalDividendEarned(new BigDecimal("10"))
                .totalRoyaltyEarned(new BigDecimal("30"))
                .totalAdjustmentAmount(new BigDecimal("0"))
                .updatedAt(Instant.parse("2026-03-10T10:15:30Z"))
                .build();

        FinanceWalletTransaction inTx = FinanceWalletTransaction.builder()
                .id(101L)
                .wallet(aliceWallet)
                .project(ventureA)
                .transactionType(FinanceWalletTransactionType.DIVIDEND)
                .cashFlowDirection(FinanceCashFlowDirection.IN)
                .amount(new BigDecimal("300"))
                .balanceAfter(new BigDecimal("120"))
                .createdAt(Instant.parse("2026-03-11T09:00:00Z"))
                .build();
        FinanceWalletTransaction outTx = FinanceWalletTransaction.builder()
                .id(102L)
                .wallet(bobWallet)
                .project(ventureB)
                .transactionType(FinanceWalletTransactionType.WITHDRAWAL)
                .cashFlowDirection(FinanceCashFlowDirection.OUT)
                .amount(new BigDecimal("140"))
                .balanceAfter(new BigDecimal("90"))
                .createdAt(Instant.parse("2026-03-11T08:00:00Z"))
                .build();
        FinanceWalletTransaction adjustmentTx = FinanceWalletTransaction.builder()
                .id(103L)
                .wallet(aliceWallet)
                .project(ventureA)
                .transactionType(FinanceWalletTransactionType.ADJUSTMENT)
                .cashFlowDirection(FinanceCashFlowDirection.IN)
                .amount(new BigDecimal("25"))
                .balanceAfter(new BigDecimal("145"))
                .createdAt(Instant.parse("2026-03-11T09:30:00Z"))
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

        FinanceVentureProfile profileA = FinanceVentureProfile.builder()
                .project(ventureA)
                .legacyVentureId(1001L)
                .displayName("Finance Venture A")
                .legacyStage("ACTIVE")
                .build();
        FinanceVentureProfile profileB = FinanceVentureProfile.builder()
                .project(ventureB)
                .legacyVentureId(1002L)
                .displayName("Finance Venture B")
                .legacyStage("RISK")
                .build();

        when(clearingSheetRepository.findAll()).thenReturn(List.of(profitSheet, lossSheet));
        when(costSummaryRepository.findAll()).thenReturn(List.of(FinanceCostSummary.builder().ledgerMonth("2026-03").build()));
        when(walletAccountRepository.findAll()).thenReturn(List.of(aliceWallet, bobWallet));
        when(walletTransactionRepository.findAll()).thenReturn(List.of(inTx, outTx, adjustmentTx));
        when(adjustmentLogRepository.findAll()).thenReturn(List.of(debitAdjustment, creditAdjustment));
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.of(snapshot));
        when(ventureProfileRepository.findAll()).thenReturn(List.of(profileA, profileB));
        when(sysProjectRepository.findAll()).thenReturn(List.of(ventureA, ventureB));

        FinanceStatementsResponse response = financeReportingService.getStatements();

        assertEquals("2026-03", response.getLatestLedgerMonth());
        assertEquals(new BigDecimal("1600.00"), response.getIncomeStatement().getTotalRevenue());
        assertEquals(new BigDecimal("1350.00"), response.getIncomeStatement().getTotalCost());
        assertEquals(new BigDecimal("16.00"), response.getIncomeStatement().getTotalMiddlewareFee());
        assertEquals(new BigDecimal("340.00"), response.getIncomeStatement().getTotalProfit());
        assertEquals(new BigDecimal("126.00"), response.getIncomeStatement().getTotalLoss());
        assertEquals(new BigDecimal("20.00"), response.getReconciliation().getAdjustmentNet());
        assertEquals(new BigDecimal("180.00"), response.getReconciliation().getTheoreticalBalance());
        assertEquals(new BigDecimal("10.00"), response.getReconciliation().getVariance());
        assertFalse(response.getReconciliation().isMatched());
        assertEquals(1, response.getRiskRows().size());
        assertEquals("Finance Venture B", response.getRiskRows().get(0).getVenture().getDisplayName());
        assertEquals(2, response.getTrend().size());
        assertEquals(new BigDecimal("2.00"), response.getKpis().get(3).getValue());
        assertEquals(new BigDecimal("1.00"), response.getKpis().get(4).getValue());
    }

    @Test
    void filtersTransactionsAndBuildsWalletSummary() {
        User alice = user("U-1", "alice", "Alice", "BUSINESS");
        User bob = user("U-2", "bob", "Bob", "DEV");
        SysProject venture = project("P-1", "Venture A", alice);
        FinanceVentureProfile profile = FinanceVentureProfile.builder()
                .project(venture)
                .legacyVentureId(1001L)
                .displayName("Finance Venture A")
                .legacyStage("ACTIVE")
                .build();

        FinanceWalletAccount aliceWallet = FinanceWalletAccount.builder()
                .id(11L)
                .owner(alice)
                .balance(new BigDecimal("120"))
                .totalDividendEarned(new BigDecimal("80"))
                .totalRoyaltyEarned(new BigDecimal("20"))
                .totalAdjustmentAmount(new BigDecimal("5"))
                .updatedAt(Instant.parse("2026-03-11T10:15:30Z"))
                .build();
        FinanceWalletAccount bobWallet = FinanceWalletAccount.builder()
                .id(12L)
                .owner(bob)
                .balance(new BigDecimal("90"))
                .totalDividendEarned(new BigDecimal("10"))
                .totalRoyaltyEarned(new BigDecimal("30"))
                .totalAdjustmentAmount(BigDecimal.ZERO)
                .updatedAt(Instant.parse("2026-03-10T10:15:30Z"))
                .build();

        FinanceWalletTransaction dividendTx = FinanceWalletTransaction.builder()
                .id(201L)
                .wallet(aliceWallet)
                .project(venture)
                .transactionType(FinanceWalletTransactionType.DIVIDEND)
                .cashFlowDirection(FinanceCashFlowDirection.IN)
                .amount(new BigDecimal("88"))
                .balanceAfter(new BigDecimal("120"))
                .sourceTable("finance_dividend_sheet")
                .sourceId(1L)
                .remark("dividend")
                .createdAt(Instant.parse("2026-03-11T09:00:00Z"))
                .build();
        FinanceWalletTransaction royaltyTx = FinanceWalletTransaction.builder()
                .id(202L)
                .wallet(bobWallet)
                .project(venture)
                .transactionType(FinanceWalletTransactionType.ROYALTY)
                .cashFlowDirection(FinanceCashFlowDirection.IN)
                .amount(new BigDecimal("30"))
                .balanceAfter(new BigDecimal("90"))
                .sourceTable("finance_middleware_usage")
                .sourceId(2L)
                .remark("royalty")
                .createdAt(Instant.parse("2026-03-10T09:00:00Z"))
                .build();

        when(walletAccountRepository.findAll()).thenReturn(List.of(aliceWallet, bobWallet));
        when(walletTransactionRepository.findAll()).thenReturn(List.of(dividendTx, royaltyTx));
        when(ventureProfileRepository.findAll()).thenReturn(List.of(profile));

        FinanceWalletOverviewResponse wallets = financeReportingService.getWalletOverview();
        FinanceTransactionListResponse transactions = financeReportingService.getTransactions(5, "U-1", FinanceWalletTransactionType.DIVIDEND, FinanceCashFlowDirection.IN, "finance_dividend_sheet");

        assertEquals(2, wallets.getSummary().getWalletCount());
        assertEquals(new BigDecimal("210.00"), wallets.getSummary().getTotalBalance());
        assertEquals("Alice", wallets.getWallets().get(0).getOwner().getName());
        assertEquals(1L, transactions.getTotalCount());
        assertEquals(1, transactions.getItems().size());
        assertEquals("Finance Venture A", transactions.getItems().get(0).getVenture().getDisplayName());
        assertEquals("finance_dividend_sheet", transactions.getItems().get(0).getAudit().getSourceTable());
    }

    @Test
    void recordsBankBalanceSnapshot() {
        when(bankBalanceSnapshotRepository.save(any(FinanceBankBalanceSnapshot.class))).thenAnswer(invocation -> {
            FinanceBankBalanceSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(55L);
            return snapshot;
        });

        FinanceBankBalanceRequest request = FinanceBankBalanceRequest.builder()
                .balance(new BigDecimal("123.456"))
                .operator("  auditor ")
                .remark("manual close")
                .build();

        var result = financeReportingService.recordBankBalance(request);

        assertEquals("55", result.getId());
        assertEquals("bank balance snapshot recorded", result.getMessage());

        ArgumentCaptor<FinanceBankBalanceSnapshot> captor = ArgumentCaptor.forClass(FinanceBankBalanceSnapshot.class);
        verify(bankBalanceSnapshotRepository).save(captor.capture());
        assertEquals(new BigDecimal("123.46"), captor.getValue().getBalance());
        assertEquals("auditor", captor.getValue().getOperator());
        assertEquals("manual close", captor.getValue().getRemark());
        assertNotNull(captor.getValue().getSnapshotAt());
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
}
