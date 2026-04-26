package com.smartlab.erp.finance;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.MiddlewareAsset;
import com.smartlab.erp.entity.MiddlewareRoyaltyRoster;
import com.smartlab.erp.entity.ProjectMemberParticipationHistory;
import com.smartlab.erp.entity.ProjectStatus;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.finance.dto.FinanceClearingExecuteRequest;
import com.smartlab.erp.finance.dto.FinanceClearingExecuteResponse;
import com.smartlab.erp.finance.dto.FinanceCostBatchRunResponse;
import com.smartlab.erp.finance.entity.BatchProjectCostControl;
import com.smartlab.erp.finance.entity.FinanceClearingSheet;
import com.smartlab.erp.finance.entity.FinanceCostBatch;
import com.smartlab.erp.finance.entity.FinanceCostSummary;
import com.smartlab.erp.finance.entity.FinanceMiddlewareUsage;
import com.smartlab.erp.finance.entity.FinanceVentureProfile;
import com.smartlab.erp.finance.entity.FinanceWalletAccount;
import com.smartlab.erp.finance.enums.FinanceBatchStatus;
import com.smartlab.erp.finance.repository.BatchProjectCostControlRepository;
import com.smartlab.erp.finance.repository.FinanceClearingSheetRepository;
import com.smartlab.erp.finance.repository.FinanceCostBatchRepository;
import com.smartlab.erp.finance.repository.FinanceCostEntryRepository;
import com.smartlab.erp.finance.repository.FinanceCostSummaryRepository;
import com.smartlab.erp.finance.repository.FinanceMiddlewareUsageRepository;
import com.smartlab.erp.finance.repository.FinanceVentureProfileRepository;
import com.smartlab.erp.finance.repository.FinanceWalletAccountRepository;
import com.smartlab.erp.finance.repository.FinanceWalletTransactionRepository;
import com.smartlab.erp.finance.service.FinanceClearingService;
import com.smartlab.erp.finance.service.FinanceCostBatchService;
import com.smartlab.erp.finance.service.FinanceReferenceService;
import com.smartlab.erp.repository.MiddlewareAssetRepository;
import com.smartlab.erp.repository.MiddlewareRoyaltyRosterRepository;
import com.smartlab.erp.repository.ProjectMemberParticipationHistoryRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.service.ProjectMemberParticipationService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceWorkbenchServiceTest {

    @Mock
    private FinanceCostBatchRepository costBatchRepository;
    @Mock
    private FinanceCostEntryRepository costEntryRepository;
    @Mock
    private FinanceCostSummaryRepository costSummaryRepository;
    @Mock
    private FinanceVentureProfileRepository ventureProfileRepository;
    @Mock
    private ProjectMemberParticipationHistoryRepository participationHistoryRepository;
    @Mock
    private ProjectMemberParticipationService participationService;
    @Mock
    private SysProjectMemberRepository projectMemberRepository;
    @Mock
    private BatchProjectCostControlRepository batchProjectCostControlRepository;
    @Mock
    private SysProjectRepository projectRepository;
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
    private FinanceCostBatchService financeCostBatchService;
    @InjectMocks
    private FinanceClearingService financeClearingService;

    @Test
    void runBatchBuildsSettlementCostsFromProjectMembers() {
        User manager = User.builder().userId("U-1").name("Alice").build();
        User memberA = User.builder().userId("U-2").name("Bob").dailyWage(new BigDecimal("300.00")).build();
        User memberB = User.builder().userId("U-3").name("Cara").dailyWage(new BigDecimal("300.00")).build();
        SysProject project = SysProject.builder()
                .projectId("P-1")
                .flowType(FlowType.PROJECT)
                .projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(manager)
                .cost(new BigDecimal("0"))
                .build();
        FinanceVentureProfile venture = FinanceVentureProfile.builder()
                .legacyVentureId(101L)
                .project(project)
                .displayName("V-101")
                .build();

        Instant dayStart = java.time.LocalDate.parse("2026-03-01").atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();

        ProjectMemberParticipationHistory historyA = ProjectMemberParticipationHistory.builder()
                .project(project).user(memberA).joinedAt(dayStart).leftAt(null).build();
        ProjectMemberParticipationHistory historyB = ProjectMemberParticipationHistory.builder()
                .project(project).user(memberB).joinedAt(dayStart).leftAt(null).build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.empty());
        when(costBatchRepository.save(any(FinanceCostBatch.class))).thenAnswer(invocation -> {
            FinanceCostBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(10L);
            return batch;
        });
        when(projectRepository.findAll()).thenReturn(List.of(project));
        doNothing().when(participationService).ensureCurrentMemberHistories(any());
        when(participationHistoryRepository.findByProject_ProjectId("P-1")).thenReturn(List.of(historyA, historyB));
        when(ventureProfileRepository.findByProject_ProjectId("P-1")).thenReturn(Optional.of(venture));

        when(projectMemberRepository.findByProjectIdInWithUser(any())).thenReturn(List.of(
                SysProjectMember.builder().projectId("P-1").user(memberA).weight(0).build(),
                SysProjectMember.builder().projectId("P-1").user(memberB).weight(0).build()
        ));
        when(batchProjectCostControlRepository.findAllById(any())).thenReturn(List.of(
                BatchProjectCostControl.builder().projectId("P-1").enabled(true).priority(0).build()
        ));
        when(costEntryRepository.existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(any(), any(), any())).thenReturn(false);
        when(costSummaryRepository.findByProject_ProjectIdAndLedgerMonth("P-1", "2026-03")).thenReturn(Optional.empty());

        FinanceCostBatchRunResponse response = financeCostBatchService.runBatch("2026-03");

        assertEquals(10L, response.getBatchId());
        assertEquals(FinanceBatchStatus.COMPLETED, response.getStatus());
        assertEquals(62, response.getGeneratedRecordCount());
        assertEquals(new BigDecimal("18600.00"), response.getTotalSettlementCost());
        verify(costEntryRepository).saveAll(any());
        verify(costSummaryRepository).save(any(FinanceCostSummary.class));
    }

    @Test
    void executeClearingTransfersLossToCompanyAndPostsRoyalty() {
        User manager = User.builder().userId("M-1").name("Manager").build();
        User sourceManager = User.builder().userId("S-1").name("Source Manager").build();
        User developerA = User.builder().userId("D-1").name("Dev A").build();
        User developerB = User.builder().userId("D-2").name("Dev B").build();
        SysProject callerProject = SysProject.builder().projectId("P-1").manager(manager).build();
        SysProject sourceProject = SysProject.builder().projectId("P-2").manager(sourceManager).build();
        FinanceVentureProfile venture = FinanceVentureProfile.builder()
                .legacyVentureId(201L)
                .project(callerProject)
                .displayName("V-201")
                .build();
        FinanceCostSummary summary = FinanceCostSummary.builder()
                .project(callerProject)
                .ledgerMonth("2026-03")
                .totalSettlementCost(new BigDecimal("1200.00"))
                .build();
        MiddlewareAsset middleware = MiddlewareAsset.builder()
                .id(99L)
                .name("Ranker")
                .sourceProjectId("P-2")
                .build();
        FinanceWalletAccount walletA = FinanceWalletAccount.builder().owner(developerA).balance(BigDecimal.ZERO).totalRoyaltyEarned(BigDecimal.ZERO).build();
        FinanceWalletAccount walletB = FinanceWalletAccount.builder().owner(developerB).balance(BigDecimal.ZERO).totalRoyaltyEarned(BigDecimal.ZERO).build();

        when(ventureProfileRepository.findByLegacyVentureId(201L)).thenReturn(Optional.of(venture));
        when(costSummaryRepository.findTopByProject_ProjectIdOrderByLedgerMonthDescIdDesc("P-1")).thenReturn(Optional.of(summary));
        when(clearingSheetRepository.findByProject_ProjectIdAndLedgerMonth("P-1", "2026-03")).thenReturn(Optional.empty());
        when(clearingSheetRepository.save(any(FinanceClearingSheet.class))).thenAnswer(invocation -> {
            FinanceClearingSheet sheet = invocation.getArgument(0);
            if (sheet.getId() == null) {
                sheet.setId(77L);
            }
            return sheet;
        });
        when(middlewareUsageRepository.findByCallerProject_ProjectIdAndLedgerMonthAndClearingSheetIsNull("P-1", "2026-03"))
                .thenReturn(List.of(FinanceMiddlewareUsage.builder()
                        .middleware(middleware)
                        .callerProject(callerProject)
                        .sourceProject(sourceProject)
                        .ledgerMonth("2026-03")
                        .royaltyFee(BigDecimal.ZERO)
                        .build()));
        when(royaltyRosterRepository.findByMiddleware_IdOrderByIdAsc(99L)).thenReturn(List.of(
                MiddlewareRoyaltyRoster.builder().userId("D-1").royaltyRatio(new BigDecimal("0.6000")).build(),
                MiddlewareRoyaltyRoster.builder().userId("D-2").royaltyRatio(new BigDecimal("0.4000")).build()
        ));
        when(financeReferenceService.getRequiredUser("D-1")).thenReturn(developerA);
        when(financeReferenceService.getRequiredUser("D-2")).thenReturn(developerB);
        when(financeReferenceService.getOrCreateWallet("D-1")).thenReturn(walletA);
        when(financeReferenceService.getOrCreateWallet("D-2")).thenReturn(walletB);

        FinanceClearingExecuteResponse response = financeClearingService.execute(FinanceClearingExecuteRequest.builder()
                .ventureId(201L)
                .finalRevenue(new BigDecimal("1000.00"))
                .build());

        assertEquals(new BigDecimal("10.00"), response.getMiddlewareFee());
        assertEquals(new BigDecimal("0.00"), response.getNetProfit());
        assertEquals(new BigDecimal("210.00"), response.getCarryForwardLoss());
        assertEquals(2, response.getRoyaltyItems().size());
        assertEquals(new BigDecimal("6.00"), response.getRoyaltyItems().get(0).getAmount());
        assertEquals(new BigDecimal("4.00"), response.getRoyaltyItems().get(1).getAmount());

        ArgumentCaptor<FinanceWalletAccount> walletCaptor = ArgumentCaptor.forClass(FinanceWalletAccount.class);
        verify(walletAccountRepository, org.mockito.Mockito.times(2)).save(walletCaptor.capture());
        List<FinanceWalletAccount> savedWallets = walletCaptor.getAllValues();
        assertNotNull(savedWallets);
        assertEquals(new BigDecimal("6.00"), savedWallets.get(0).getBalance());
        assertEquals(new BigDecimal("4.00"), savedWallets.get(1).getBalance());
        verify(walletTransactionRepository, org.mockito.Mockito.times(2)).save(any());
        verify(middlewareUsageRepository).save(any());
    }
}
