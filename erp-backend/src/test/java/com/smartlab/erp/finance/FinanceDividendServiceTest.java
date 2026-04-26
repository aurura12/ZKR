package com.smartlab.erp.finance;

import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectStatus;
import com.smartlab.erp.finance.dto.FinanceDividendConfirmRequest;
import com.smartlab.erp.finance.dto.FinanceDividendPrepareRequest;
import com.smartlab.erp.finance.dto.FinanceDividendPrepareResponse;
import com.smartlab.erp.finance.entity.*;
import com.smartlab.erp.finance.enums.FinanceCashFlowDirection;
import com.smartlab.erp.finance.enums.FinanceClearingStatus;
import com.smartlab.erp.finance.enums.FinanceDividendStatus;
import com.smartlab.erp.finance.repository.*;
import com.smartlab.erp.finance.service.FinanceDividendService;
import com.smartlab.erp.finance.service.FinanceReferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceDividendServiceTest {

    @Mock
    private FinanceReferenceService referenceService;
    @Mock
    private FinanceClearingSheetRepository clearingSheetRepository;
    @Mock
    private FinanceDividendSheetRepository dividendSheetRepository;
    @Mock
    private FinanceVentureEquityRepository ventureEquityRepository;
    @Mock
    private FinanceVentureProfileRepository ventureProfileRepository;
    @Mock
    private FinanceWalletAccountRepository walletAccountRepository;
    @Mock
    private FinanceWalletTransactionRepository walletTransactionRepository;
    @Mock
    private FinanceBankBalanceSnapshotRepository bankBalanceSnapshotRepository;

    @InjectMocks
    private FinanceDividendService dividendService;

    @Test
    void prepareBalancesTailDifferenceIntoLastHolder() {
        SysProject project = project("P-1");
        FinanceVentureProfile profile = profile(project, 11L, "Alpha");
        FinanceClearingSheet clearingSheet = FinanceClearingSheet.builder()
                .project(project)
                .ledgerMonth("2026-03")
                .netProfit(new BigDecimal("100.00"))
                .status(FinanceClearingStatus.CLEARED)
                .build();
        FinanceVentureEquity first = equity(project, user("U-1", "alice"), new BigDecimal("0.3333"));
        FinanceVentureEquity second = equity(project, user("U-2", "bob"), new BigDecimal("0.3333"));
        FinanceVentureEquity third = equity(project, user("U-3", "carol"), new BigDecimal("0.3334"));

        when(referenceService.getRequiredProject("P-1")).thenReturn(project);
        when(ventureProfileRepository.findByProject_ProjectId("P-1")).thenReturn(Optional.of(profile));
        when(clearingSheetRepository.findTopByProject_ProjectIdOrderByIdDesc("P-1")).thenReturn(Optional.of(clearingSheet));
        when(dividendSheetRepository.existsByProject_ProjectIdAndStatus("P-1", FinanceDividendStatus.PENDING)).thenReturn(false);
        when(ventureEquityRepository.findByProject_ProjectIdAndActiveTrue("P-1")).thenReturn(List.of(first, second, third));
        when(dividendSheetRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FinanceDividendPrepareResponse response = dividendService.prepare(FinanceDividendPrepareRequest.builder()
                .projectId("P-1")
                .build());

        assertEquals(3, response.getItems().size());
        assertEquals(new BigDecimal("33.33"), response.getItems().get(0).getAmount());
        assertEquals(new BigDecimal("33.33"), response.getItems().get(1).getAmount());
        assertEquals(new BigDecimal("33.34"), response.getItems().get(2).getAmount());
        assertEquals(new BigDecimal("100.00"), response.getTotalAmount());
    }

    @Test
    void prepareRejectsWhenPendingDividendSheetsAlreadyExistForVenture() {
        SysProject project = project("P-1");
        FinanceVentureProfile profile = profile(project, 11L, "Alpha");
        FinanceClearingSheet clearingSheet = FinanceClearingSheet.builder()
                .project(project)
                .ledgerMonth("2026-03")
                .netProfit(new BigDecimal("100.00"))
                .status(FinanceClearingStatus.CLEARED)
                .build();

        when(referenceService.getRequiredProject("P-1")).thenReturn(project);
        when(ventureProfileRepository.findByProject_ProjectId("P-1")).thenReturn(Optional.of(profile));
        when(clearingSheetRepository.findTopByProject_ProjectIdOrderByIdDesc("P-1")).thenReturn(Optional.of(clearingSheet));
        when(dividendSheetRepository.existsByProject_ProjectIdAndStatus("P-1", FinanceDividendStatus.PENDING)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> dividendService.prepare(
                FinanceDividendPrepareRequest.builder().projectId("P-1").build()));

        assertEquals("Pending dividend sheets already exist for venture: P-1", ex.getMessage());
        verify(dividendSheetRepository, never()).saveAll(any());
    }

    @Test
    void confirmRejectsWhenPendingTotalExceedsLatestBankBalance() {
        SysProject project = project("P-1");
        FinanceDividendSheet firstSheet = FinanceDividendSheet.builder()
                .id(10L)
                .project(project)
                .user(user("U-1", "alice"))
                .ledgerMonth("2026-03")
                .amount(new BigDecimal("40.00"))
                .status(FinanceDividendStatus.PENDING)
                .build();
        FinanceDividendSheet secondSheet = FinanceDividendSheet.builder()
                .id(11L)
                .project(project)
                .user(user("U-2", "bob"))
                .ledgerMonth("2026-03")
                .amount(new BigDecimal("35.00"))
                .status(FinanceDividendStatus.PENDING)
                .build();
        when(dividendSheetRepository.findByProject_ProjectIdAndStatus("P-1", FinanceDividendStatus.PENDING))
                .thenReturn(List.of(firstSheet, secondSheet));
        when(ventureProfileRepository.findByProject_ProjectId("P-1")).thenReturn(Optional.of(profile(project, 11L, "Alpha")));
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.of(FinanceBankBalanceSnapshot.builder()
                .balance(new BigDecimal("70.00"))
                .operator("ops")
                .build()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> dividendService.confirm(
                FinanceDividendConfirmRequest.builder().projectId("P-1").build(),
                "ops-user"));

        assertEquals("Pending dividend total 75.00 exceeds latest bank balance 70.00", ex.getMessage());
        verify(walletAccountRepository, never()).save(any());
        verify(bankBalanceSnapshotRepository, never()).save(any(FinanceBankBalanceSnapshot.class));
    }

    @Test
    void confirmPostsWalletsAndCreatesSnapshot() {
        SysProject project = project("P-1");
        User user = user("U-1", "alice");
        FinanceVentureProfile profile = profile(project, 11L, "Alpha");
        FinanceDividendSheet sheet = FinanceDividendSheet.builder()
                .id(10L)
                .project(project)
                .user(user)
                .ledgerMonth("2026-03")
                .amount(new BigDecimal("30.00"))
                .status(FinanceDividendStatus.PENDING)
                .build();
        FinanceWalletAccount wallet = FinanceWalletAccount.builder()
                .id(21L)
                .owner(user)
                .balance(new BigDecimal("10.00"))
                .totalDividendEarned(BigDecimal.ZERO)
                .totalRoyaltyEarned(BigDecimal.ZERO)
                .totalAdjustmentAmount(BigDecimal.ZERO)
                .build();

        when(dividendSheetRepository.findByProject_ProjectIdAndStatus("P-1", FinanceDividendStatus.PENDING)).thenReturn(List.of(sheet));
        when(ventureProfileRepository.findByProject_ProjectId("P-1")).thenReturn(Optional.of(profile));
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.of(FinanceBankBalanceSnapshot.builder()
                .balance(new BigDecimal("100.00"))
                .operator("ops")
                .build()));
        when(referenceService.getOrCreateWallet("U-1")).thenReturn(wallet);
        when(walletAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        dividendService.confirm(FinanceDividendConfirmRequest.builder()
                .projectId("P-1")
                .remark("confirm")
                .build(), "ops-user");

        assertEquals(FinanceDividendStatus.CONFIRMED, sheet.getStatus());
        assertEquals(new BigDecimal("40.00"), wallet.getBalance());
        verify(walletTransactionRepository).save(any(FinanceWalletTransaction.class));
        ArgumentCaptor<FinanceBankBalanceSnapshot> snapshotCaptor = ArgumentCaptor.forClass(FinanceBankBalanceSnapshot.class);
        verify(bankBalanceSnapshotRepository).save(snapshotCaptor.capture());
        assertEquals(new BigDecimal("70.00"), snapshotCaptor.getValue().getBalance());
    }

    @Test
    void confirmReturnsStableFinalizedOutcomeWhenAlreadyConfirmed() {
        SysProject project = project("P-1");
        FinanceVentureProfile profile = profile(project, 11L, "Alpha");
        FinanceDividendSheet confirmedSheet = FinanceDividendSheet.builder()
                .id(10L)
                .project(project)
                .user(user("U-1", "alice"))
                .ledgerMonth("2026-03")
                .amount(new BigDecimal("30.00"))
                .status(FinanceDividendStatus.CONFIRMED)
                .confirmedBy("ops-user")
                .confirmedAt(java.time.Instant.parse("2026-03-12T08:00:00Z"))
                .build();

        when(dividendSheetRepository.findByProject_ProjectIdAndStatus("P-1", FinanceDividendStatus.PENDING))
                .thenReturn(List.of());
        when(dividendSheetRepository.findByProject_ProjectIdAndStatusOrderByConfirmedAtDescIdDesc("P-1", FinanceDividendStatus.CONFIRMED))
                .thenReturn(List.of(confirmedSheet));
        when(ventureProfileRepository.findByProject_ProjectId("P-1")).thenReturn(Optional.of(profile));
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.of(FinanceBankBalanceSnapshot.builder()
                .balance(new BigDecimal("70.00"))
                .operator("ops")
                .build()));

        var response = dividendService.confirm(FinanceDividendConfirmRequest.builder()
                .projectId("P-1")
                .build(), "ops-user");

        assertEquals(1, response.getConfirmedCount());
        assertEquals(new BigDecimal("30.00"), response.getTotalAmount());
        assertEquals(new BigDecimal("100.00"), response.getBankBalanceBefore());
        assertEquals(new BigDecimal("70.00"), response.getBankBalanceAfter());
        assertEquals("2026-03", response.getLedgerMonth());
        assertNotNull(response.getWalletResults());
        assertEquals(0, response.getWalletResults().size());
        verify(referenceService, never()).getOrCreateWallet(any());
        verify(walletTransactionRepository, never()).save(any(FinanceWalletTransaction.class));
        verify(bankBalanceSnapshotRepository, never()).save(any(FinanceBankBalanceSnapshot.class));
    }

    @Test
    void confirmPersistsInboundWalletTransactionForDividendCredit() {
        SysProject project = project("P-1");
        User user = user("U-1", "alice");
        FinanceVentureProfile profile = profile(project, 11L, "Alpha");
        FinanceDividendSheet sheet = FinanceDividendSheet.builder()
                .id(10L)
                .project(project)
                .user(user)
                .ledgerMonth("2026-03")
                .amount(new BigDecimal("30.00"))
                .status(FinanceDividendStatus.PENDING)
                .build();
        FinanceWalletAccount wallet = FinanceWalletAccount.builder()
                .id(21L)
                .owner(user)
                .balance(new BigDecimal("10.00"))
                .totalDividendEarned(BigDecimal.ZERO)
                .totalRoyaltyEarned(BigDecimal.ZERO)
                .totalAdjustmentAmount(BigDecimal.ZERO)
                .build();

        when(dividendSheetRepository.findByProject_ProjectIdAndStatus("P-1", FinanceDividendStatus.PENDING)).thenReturn(List.of(sheet));
        when(ventureProfileRepository.findByProject_ProjectId("P-1")).thenReturn(Optional.of(profile));
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.of(FinanceBankBalanceSnapshot.builder()
                .balance(new BigDecimal("100.00"))
                .operator("ops")
                .build()));
        when(referenceService.getOrCreateWallet("U-1")).thenReturn(wallet);
        when(walletAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        dividendService.confirm(FinanceDividendConfirmRequest.builder()
                .projectId("P-1")
                .remark("confirm")
                .build(), "ops-user");

        ArgumentCaptor<FinanceWalletTransaction> transactionCaptor = ArgumentCaptor.forClass(FinanceWalletTransaction.class);
        verify(walletTransactionRepository).save(transactionCaptor.capture());
        assertEquals(FinanceCashFlowDirection.IN, transactionCaptor.getValue().getCashFlowDirection());
    }

    @Test
    void confirmRetryReturnsLatestConfirmedBatchOnlyWhenProjectHasHistory() {
        SysProject project = project("P-1");
        FinanceVentureProfile profile = profile(project, 11L, "Alpha");
        java.time.Instant olderBatchAt = java.time.Instant.parse("2026-02-12T08:00:00Z");
        java.time.Instant latestBatchAt = java.time.Instant.parse("2026-03-12T08:00:00Z");
        FinanceDividendSheet latestFirst = confirmedSheet(project, "U-2", "bob", "2026-03", "20.00", latestBatchAt);
        FinanceDividendSheet latestSecond = confirmedSheet(project, "U-3", "carol", "2026-03", "15.00", latestBatchAt);
        FinanceDividendSheet older = confirmedSheet(project, "U-1", "alice", "2026-02", "30.00", olderBatchAt);

        when(dividendSheetRepository.findByProject_ProjectIdAndStatus("P-1", FinanceDividendStatus.PENDING))
                .thenReturn(List.of());
        when(dividendSheetRepository.findByProject_ProjectIdAndStatusOrderByConfirmedAtDescIdDesc("P-1", FinanceDividendStatus.CONFIRMED))
                .thenReturn(List.of(latestSecond, latestFirst, older));
        when(ventureProfileRepository.findByProject_ProjectId("P-1")).thenReturn(Optional.of(profile));
        when(bankBalanceSnapshotRepository.findTopByOrderBySnapshotAtDesc()).thenReturn(Optional.of(FinanceBankBalanceSnapshot.builder()
                .balance(new BigDecimal("65.00"))
                .operator("ops")
                .build()));

        var response = dividendService.confirm(FinanceDividendConfirmRequest.builder()
                .projectId("P-1")
                .build(), "ops-user");

        assertEquals(2, response.getConfirmedCount());
        assertEquals(new BigDecimal("35.00"), response.getTotalAmount());
        assertEquals(new BigDecimal("100.00"), response.getBankBalanceBefore());
        assertEquals(new BigDecimal("65.00"), response.getBankBalanceAfter());
        assertEquals("2026-03", response.getLedgerMonth());
        verify(referenceService, never()).getOrCreateWallet(any());
        verify(bankBalanceSnapshotRepository, never()).save(any(FinanceBankBalanceSnapshot.class));
    }

    private SysProject project(String projectId) {
        SysProject project = new SysProject();
        project.setProjectId(projectId);
        project.setFlowType(FlowType.PROJECT);
        project.setProjectStatus(ProjectStatus.SETTLEMENT);
        return project;
    }

    private FinanceVentureProfile profile(SysProject project, Long legacyId, String displayName) {
        return FinanceVentureProfile.builder()
                .project(project)
                .legacyVentureId(legacyId)
                .displayName(displayName)
                .legacyStage("CLEARED")
                .build();
    }

    private FinanceDividendSheet confirmedSheet(SysProject project, String userId, String username, String ledgerMonth,
                                                String amount, java.time.Instant confirmedAt) {
        return FinanceDividendSheet.builder()
                .project(project)
                .user(user(userId, username))
                .ledgerMonth(ledgerMonth)
                .amount(new BigDecimal(amount))
                .status(FinanceDividendStatus.CONFIRMED)
                .confirmedBy("ops-user")
                .confirmedAt(confirmedAt)
                .build();
    }

    private FinanceVentureEquity equity(SysProject project, User user, BigDecimal ratio) {
        return FinanceVentureEquity.builder()
                .project(project)
                .user(user)
                .equityRatio(ratio)
                .dividendRatio(ratio)
                .active(Boolean.TRUE)
                .build();
    }

    private User user(String userId, String username) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setName(username);
        user.setRole("RESEARCH");
        return user;
    }
}
