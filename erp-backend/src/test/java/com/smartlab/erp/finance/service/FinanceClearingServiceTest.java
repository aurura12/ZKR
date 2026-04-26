package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.MiddlewareAsset;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.finance.dto.FinanceClearingExecuteRequest;
import com.smartlab.erp.finance.dto.FinanceClearingExecuteResponse;
import com.smartlab.erp.finance.dto.FinanceClearingVentureView;
import com.smartlab.erp.finance.entity.FinanceClearingSheet;
import com.smartlab.erp.finance.entity.FinanceCostSummary;
import com.smartlab.erp.finance.entity.FinanceMiddlewareUsage;
import com.smartlab.erp.finance.entity.FinanceVentureProfile;
import com.smartlab.erp.finance.entity.FinanceWalletAccount;
import com.smartlab.erp.finance.enums.FinanceClearingStatus;
import com.smartlab.erp.finance.repository.FinanceClearingSheetRepository;
import com.smartlab.erp.finance.repository.FinanceCostSummaryRepository;
import com.smartlab.erp.finance.repository.FinanceMiddlewareUsageRepository;
import com.smartlab.erp.finance.repository.FinanceVentureProfileRepository;
import com.smartlab.erp.finance.repository.FinanceWalletAccountRepository;
import com.smartlab.erp.finance.repository.FinanceWalletTransactionRepository;
import com.smartlab.erp.repository.MiddlewareAssetRepository;
import com.smartlab.erp.repository.MiddlewareRoyaltyRosterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceClearingServiceTest {

    @Mock
    private FinanceVentureProfileRepository ventureProfileRepository;
    @Mock
    private FinanceCostSummaryRepository costSummaryRepository;
    @Mock
    private FinanceClearingSheetRepository clearingSheetRepository;
    @Mock
    private FinanceMiddlewareUsageRepository middlewareUsageRepository;
    @Mock
    private FinanceWalletAccountRepository walletAccountRepository;
    @Mock
    private FinanceWalletTransactionRepository walletTransactionRepository;
    @Mock
    private FinanceReferenceService financeReferenceService;
    @Mock
    private MiddlewareAssetRepository middlewareAssetRepository;
    @Mock
    private MiddlewareRoyaltyRosterRepository royaltyRosterRepository;

    @InjectMocks
    private FinanceClearingService financeClearingService;

    @Test
    void listVentures_marksLatestCycleAsPendingWhenOnlyOlderClearingExists() {
        VentureFixture fixture = ventureFixture();
        FinanceCostSummary latestSummary = FinanceCostSummary.builder()
                .project(fixture.callerProject)
                .ledgerMonth("2026-03")
                .totalSettlementCost(new BigDecimal("1200.00"))
                .build();
        FinanceClearingSheet olderSheet = FinanceClearingSheet.builder()
                .project(fixture.callerProject)
                .ledgerMonth("2026-02")
                .finalRevenue(new BigDecimal("1500.00"))
                .totalCost(new BigDecimal("1100.00"))
                .middlewareFee(new BigDecimal("10.00"))
                .netProfit(new BigDecimal("390.00"))
                .carryForwardLoss(BigDecimal.ZERO.setScale(2))
                .status(FinanceClearingStatus.CLEARED)
                .clearedAt(Instant.parse("2026-03-01T00:00:00Z"))
                .build();

        when(ventureProfileRepository.findByLedgerEnabledTrueOrderByLegacyVentureIdAsc()).thenReturn(List.of(fixture.venture));
        when(costSummaryRepository.findTopByProject_ProjectIdOrderByIdDesc("P-1")).thenReturn(Optional.of(latestSummary));
        when(clearingSheetRepository.findTopByProject_ProjectIdOrderByIdDesc("P-1")).thenReturn(Optional.of(olderSheet));

        List<FinanceClearingVentureView> ventures = financeClearingService.listVentures();

        assertEquals(1, ventures.size());
        assertEquals("2026-03", ventures.get(0).getLedgerMonth());
        assertEquals(FinanceClearingStatus.PENDING, ventures.get(0).getStatus());
        assertEquals(new BigDecimal("0.00"), ventures.get(0).getFinalRevenue());
        assertEquals(new BigDecimal("1200.00"), ventures.get(0).getTotalCost());
        assertNull(ventures.get(0).getClearedAt());
    }

    @Test
    void execute_usesBackendRecordedMiddlewareCallsWhenOnlyApprovedFieldsAreSupplied() {
        ClearingFixture fixture = clearingFixture(new BigDecimal("1200.00"));
        when(middlewareUsageRepository.findByCallerProject_ProjectIdAndLedgerMonthAndClearingSheetIsNull("P-1", "2026-03"))
                .thenReturn(List.of(fixture.firstUsage, fixture.secondUsage));
        when(royaltyRosterRepository.findByMiddleware_IdOrderByIdAsc(100L)).thenReturn(List.of());

        FinanceClearingExecuteResponse response = financeClearingService.execute(FinanceClearingExecuteRequest.builder()
                .ventureId(201L)
                .finalRevenue(new BigDecimal("2000.00"))
                .build());

        assertEquals(new BigDecimal("40.00"), response.getMiddlewareFee());
        assertEquals(new BigDecimal("760.00"), response.getNetProfit());
        assertEquals(new BigDecimal("0.00"), response.getLossTransferredToCompany());
        assertEquals(2, response.getRoyaltyItems().size());

        ArgumentCaptor<FinanceMiddlewareUsage> usageCaptor = ArgumentCaptor.forClass(FinanceMiddlewareUsage.class);
        verify(middlewareUsageRepository, org.mockito.Mockito.times(2)).save(usageCaptor.capture());
        assertEquals(new BigDecimal("20.00"), usageCaptor.getAllValues().get(0).getRoyaltyFee());
        assertEquals(new BigDecimal("20.00"), usageCaptor.getAllValues().get(1).getRoyaltyFee());
    }

    @Test
    void execute_transfersLossToCompanyWhenProfitIsNegative() {
        ClearingFixture fixture = clearingFixture(new BigDecimal("1200.00"));
        when(middlewareUsageRepository.findByCallerProject_ProjectIdAndLedgerMonthAndClearingSheetIsNull("P-1", "2026-03"))
                .thenReturn(List.of(fixture.firstUsage));

        FinanceClearingExecuteResponse response = financeClearingService.execute(FinanceClearingExecuteRequest.builder()
                .ventureId(201L)
                .finalRevenue(new BigDecimal("1000.00"))
                .build());

        assertEquals(new BigDecimal("10.00"), response.getMiddlewareFee());
        assertEquals(new BigDecimal("0.00"), response.getNetProfit());
        assertEquals(new BigDecimal("210.00"), response.getLossTransferredToCompany());
        verify(walletAccountRepository).save(any(FinanceWalletAccount.class));
        verify(walletTransactionRepository).save(any());
    }

    @Test
    void execute_returnsExistingClearingWhenCurrentCycleAlreadyCompleted() {
        VentureFixture fixture = ventureFixture();
        FinanceCostSummary summary = FinanceCostSummary.builder()
                .project(fixture.callerProject)
                .ledgerMonth("2026-03")
                .totalSettlementCost(new BigDecimal("1200.00"))
                .build();
        FinanceClearingSheet existingSheet = FinanceClearingSheet.builder()
                .id(77L)
                .project(fixture.callerProject)
                .ledgerMonth("2026-03")
                .finalRevenue(new BigDecimal("2000.00"))
                .totalCost(new BigDecimal("1200.00"))
                .middlewareFee(new BigDecimal("40.00"))
                .netProfit(new BigDecimal("760.00"))
                .carryForwardLoss(new BigDecimal("0.00"))
                .status(FinanceClearingStatus.CLEARED)
                .clearedAt(Instant.parse("2026-03-31T00:00:00Z"))
                .build();

        when(ventureProfileRepository.findByLegacyVentureId(201L)).thenReturn(Optional.of(fixture.venture));
        when(costSummaryRepository.findTopByProject_ProjectIdOrderByIdDesc("P-1")).thenReturn(Optional.of(summary));
        when(clearingSheetRepository.findByProject_ProjectIdAndLedgerMonth("P-1", "2026-03")).thenReturn(Optional.of(existingSheet));

        FinanceClearingExecuteResponse response = financeClearingService.execute(FinanceClearingExecuteRequest.builder()
                .ventureId(201L)
                .finalRevenue(new BigDecimal("9999.00"))
                .build());

        assertEquals(77L, response.getClearingSheetId());
        assertEquals(new BigDecimal("40.00"), response.getMiddlewareFee());
        assertEquals(new BigDecimal("760.00"), response.getNetProfit());
        verify(clearingSheetRepository, never()).save(any(FinanceClearingSheet.class));
        verify(middlewareUsageRepository, never()).save(any(FinanceMiddlewareUsage.class));
        verify(walletAccountRepository, never()).save(any(FinanceWalletAccount.class));
    }

    private ClearingFixture clearingFixture(BigDecimal totalSettlementCost) {
        VentureFixture ventureFixture = ventureFixture();
        FinanceCostSummary summary = FinanceCostSummary.builder()
                .project(ventureFixture.callerProject)
                .ledgerMonth("2026-03")
                .totalSettlementCost(totalSettlementCost)
                .build();
        FinanceWalletAccount sourceWallet = FinanceWalletAccount.builder()
                .owner(ventureFixture.sourceManager)
                .balance(BigDecimal.ZERO)
                .totalRoyaltyEarned(BigDecimal.ZERO)
                .build();
        FinanceMiddlewareUsage firstUsage = FinanceMiddlewareUsage.builder()
                .middleware(ventureFixture.firstMiddleware)
                .callerProject(ventureFixture.callerProject)
                .sourceProject(ventureFixture.sourceProject)
                .ledgerMonth("2026-03")
                .royaltyFee(BigDecimal.ZERO)
                .build();
        FinanceMiddlewareUsage secondUsage = FinanceMiddlewareUsage.builder()
                .middleware(ventureFixture.secondMiddleware)
                .callerProject(ventureFixture.callerProject)
                .sourceProject(ventureFixture.sourceProject)
                .ledgerMonth("2026-03")
                .royaltyFee(BigDecimal.ZERO)
                .build();

        when(ventureProfileRepository.findByLegacyVentureId(201L)).thenReturn(Optional.of(ventureFixture.venture));
        when(costSummaryRepository.findTopByProject_ProjectIdOrderByIdDesc("P-1")).thenReturn(Optional.of(summary));
        when(clearingSheetRepository.findByProject_ProjectIdAndLedgerMonth("P-1", "2026-03")).thenReturn(Optional.empty());
        when(clearingSheetRepository.save(any(FinanceClearingSheet.class))).thenAnswer(invocation -> {
            FinanceClearingSheet sheet = invocation.getArgument(0);
            if (sheet.getId() == null) {
                sheet.setId(77L);
            }
            return sheet;
        });
        when(royaltyRosterRepository.findByMiddleware_IdOrderByIdAsc(99L)).thenReturn(List.of());
        when(financeReferenceService.getOrCreateWallet("S-1")).thenReturn(sourceWallet);

        return new ClearingFixture(firstUsage, secondUsage);
    }

    private VentureFixture ventureFixture() {
        User callerManager = User.builder().userId("M-1").name("Caller Manager").build();
        User sourceManager = User.builder().userId("S-1").name("Source Manager").build();
        SysProject callerProject = SysProject.builder().projectId("P-1").manager(callerManager).build();
        SysProject sourceProject = SysProject.builder().projectId("P-2").manager(sourceManager).build();
        FinanceVentureProfile venture = FinanceVentureProfile.builder()
                .legacyVentureId(201L)
                .project(callerProject)
                .displayName("V-201")
                .build();
        MiddlewareAsset firstMiddleware = MiddlewareAsset.builder()
                .id(99L)
                .name("Ranker")
                .sourceProjectId("P-2")
                .build();
        MiddlewareAsset secondMiddleware = MiddlewareAsset.builder()
                .id(100L)
                .name("Writer")
                .sourceProjectId("P-2")
                .build();

        return new VentureFixture(venture, callerProject, sourceProject, sourceManager, firstMiddleware, secondMiddleware);
    }

    private record VentureFixture(FinanceVentureProfile venture,
                                  SysProject callerProject,
                                  SysProject sourceProject,
                                  User sourceManager,
                                  MiddlewareAsset firstMiddleware,
                                  MiddlewareAsset secondMiddleware) {
    }

    private record ClearingFixture(FinanceMiddlewareUsage firstUsage, FinanceMiddlewareUsage secondUsage) {
    }
}
