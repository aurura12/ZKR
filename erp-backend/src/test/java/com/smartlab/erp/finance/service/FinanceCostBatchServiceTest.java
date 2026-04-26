package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectMemberParticipationHistory;
import com.smartlab.erp.entity.ProjectStatus;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.finance.dto.FinanceCostBatchRunResponse;
import com.smartlab.erp.finance.entity.BatchProjectCostControl;
import com.smartlab.erp.finance.entity.FinanceCostBatch;
import com.smartlab.erp.finance.entity.FinanceCostEntry;
import com.smartlab.erp.finance.entity.FinanceCostSummary;
import com.smartlab.erp.finance.entity.FinanceVentureProfile;
import com.smartlab.erp.finance.enums.FinanceBatchStatus;
import com.smartlab.erp.finance.repository.BatchProjectCostControlRepository;
import com.smartlab.erp.finance.repository.FinanceCostBatchRepository;
import com.smartlab.erp.finance.repository.FinanceCostEntryRepository;
import com.smartlab.erp.finance.repository.FinanceCostSummaryRepository;
import com.smartlab.erp.finance.repository.FinanceVentureProfileRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceCostBatchServiceTest {

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

    @InjectMocks
    private FinanceCostBatchService financeCostBatchService;

    @Test
    void shouldDistributeEvenly() {
        User user = User.builder().userId("U-1").name("Alice").dailyWage(new BigDecimal("300.00")).build();

        SysProject projectA = SysProject.builder()
                .projectId("P-A").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();
        SysProject projectB = SysProject.builder()
                .projectId("P-B").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();

        FinanceVentureProfile ventureA = FinanceVentureProfile.builder().legacyVentureId(1L).project(projectA).displayName("VA").build();
        FinanceVentureProfile ventureB = FinanceVentureProfile.builder().legacyVentureId(2L).project(projectB).displayName("VB").build();

        Instant now = Instant.now();
        Instant dayStart = java.time.LocalDate.parse("2026-03-01").atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();

        ProjectMemberParticipationHistory historyA = ProjectMemberParticipationHistory.builder()
                .project(projectA).user(user).joinedAt(dayStart).leftAt(null).build();
        ProjectMemberParticipationHistory historyB = ProjectMemberParticipationHistory.builder()
                .project(projectB).user(user).joinedAt(dayStart).leftAt(null).build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.empty());
        when(costBatchRepository.save(any(FinanceCostBatch.class))).thenAnswer(invocation -> {
            FinanceCostBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(10L);
            return batch;
        });
        when(projectRepository.findAll()).thenReturn(List.of(projectA, projectB));
        doNothing().when(participationService).ensureCurrentMemberHistories(any());
        when(participationHistoryRepository.findByProject_ProjectId("P-A")).thenReturn(List.of(historyA));
        when(participationHistoryRepository.findByProject_ProjectId("P-B")).thenReturn(List.of(historyB));
        when(ventureProfileRepository.findByProject_ProjectId("P-A")).thenReturn(Optional.of(ventureA));
        when(ventureProfileRepository.findByProject_ProjectId("P-B")).thenReturn(Optional.of(ventureB));

        when(projectMemberRepository.findByProjectIdInWithUser(any())).thenReturn(List.of(
                SysProjectMember.builder().projectId("P-A").user(user).weight(0).build(),
                SysProjectMember.builder().projectId("P-B").user(user).weight(0).build()
        ));
        when(batchProjectCostControlRepository.findAllById(any())).thenReturn(List.of(
                BatchProjectCostControl.builder().projectId("P-A").enabled(true).priority(0).build(),
                BatchProjectCostControl.builder().projectId("P-B").enabled(true).priority(0).build()
        ));

        when(costEntryRepository.existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(any(), any(), any())).thenReturn(false);
        when(costSummaryRepository.findByProject_ProjectIdAndLedgerMonth(any(), any())).thenReturn(Optional.empty());

        FinanceCostBatchRunResponse response = financeCostBatchService.runBatch("2026-03");

        ArgumentCaptor<List<FinanceCostEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(costEntryRepository).saveAll(entriesCaptor.capture());
        List<FinanceCostEntry> savedEntries = entriesCaptor.getValue();

        assertEquals(62, savedEntries.size());
        assertTrue(savedEntries.stream().allMatch(e -> e.getFinalSettlementCost().compareTo(new BigDecimal("150.00")) == 0));
        assertTrue(savedEntries.stream().allMatch(e -> e.getLaborCost().compareTo(new BigDecimal("150.00")) == 0));
    }

    @Test
    void shouldApplyWeightMultiplier() {
        User user = User.builder().userId("U-1").name("Alice").dailyWage(new BigDecimal("300.00")).build();

        SysProject projectA = SysProject.builder()
                .projectId("P-A").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();
        SysProject projectB = SysProject.builder()
                .projectId("P-B").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();

        FinanceVentureProfile ventureA = FinanceVentureProfile.builder().legacyVentureId(1L).project(projectA).displayName("VA").build();
        FinanceVentureProfile ventureB = FinanceVentureProfile.builder().legacyVentureId(2L).project(projectB).displayName("VB").build();

        Instant dayStart = java.time.LocalDate.parse("2026-03-01").atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();

        ProjectMemberParticipationHistory historyA = ProjectMemberParticipationHistory.builder()
                .project(projectA).user(user).joinedAt(dayStart).leftAt(null).build();
        ProjectMemberParticipationHistory historyB = ProjectMemberParticipationHistory.builder()
                .project(projectB).user(user).joinedAt(dayStart).leftAt(null).build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.empty());
        when(costBatchRepository.save(any(FinanceCostBatch.class))).thenAnswer(invocation -> {
            FinanceCostBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(10L);
            return batch;
        });
        when(projectRepository.findAll()).thenReturn(List.of(projectA, projectB));
        doNothing().when(participationService).ensureCurrentMemberHistories(any());
        when(participationHistoryRepository.findByProject_ProjectId("P-A")).thenReturn(List.of(historyA));
        when(participationHistoryRepository.findByProject_ProjectId("P-B")).thenReturn(List.of(historyB));
        when(ventureProfileRepository.findByProject_ProjectId("P-A")).thenReturn(Optional.of(ventureA));
        when(ventureProfileRepository.findByProject_ProjectId("P-B")).thenReturn(Optional.of(ventureB));

        when(projectMemberRepository.findByProjectIdInWithUser(any())).thenReturn(List.of(
                SysProjectMember.builder().projectId("P-A").user(user).weight(5).build(),
                SysProjectMember.builder().projectId("P-B").user(user).weight(0).build()
        ));
        when(batchProjectCostControlRepository.findAllById(any())).thenReturn(List.of(
                BatchProjectCostControl.builder().projectId("P-A").enabled(true).priority(0).build(),
                BatchProjectCostControl.builder().projectId("P-B").enabled(true).priority(0).build()
        ));

        when(costEntryRepository.existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(any(), any(), any())).thenReturn(false);
        when(costSummaryRepository.findByProject_ProjectIdAndLedgerMonth(any(), any())).thenReturn(Optional.empty());

        financeCostBatchService.runBatch("2026-03");

        ArgumentCaptor<List<FinanceCostEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(costEntryRepository).saveAll(entriesCaptor.capture());
        List<FinanceCostEntry> savedEntries = entriesCaptor.getValue();

        assertEquals(62, savedEntries.size());
        FinanceCostEntry entryA = savedEntries.stream().filter(e -> e.getProject().getProjectId().equals("P-A")).findFirst().orElseThrow();
        FinanceCostEntry entryB = savedEntries.stream().filter(e -> e.getProject().getProjectId().equals("P-B")).findFirst().orElseThrow();

        assertEquals(new BigDecimal("225.00"), entryA.getFinalSettlementCost());
        assertEquals(new BigDecimal("150.00"), entryB.getFinalSettlementCost());
    }

    @Test
    void shouldExcludePausedProjects() {
        User user = User.builder().userId("U-1").name("Alice").dailyWage(new BigDecimal("300.00")).build();

        SysProject projectA = SysProject.builder()
                .projectId("P-A").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();
        SysProject projectB = SysProject.builder()
                .projectId("P-B").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();

        FinanceVentureProfile ventureA = FinanceVentureProfile.builder().legacyVentureId(1L).project(projectA).displayName("VA").build();
        FinanceVentureProfile ventureB = FinanceVentureProfile.builder().legacyVentureId(2L).project(projectB).displayName("VB").build();

        Instant dayStart = java.time.LocalDate.parse("2026-03-01").atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();

        ProjectMemberParticipationHistory historyA = ProjectMemberParticipationHistory.builder()
                .project(projectA).user(user).joinedAt(dayStart).leftAt(null).build();
        ProjectMemberParticipationHistory historyB = ProjectMemberParticipationHistory.builder()
                .project(projectB).user(user).joinedAt(dayStart).leftAt(null).build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.empty());
        when(costBatchRepository.save(any(FinanceCostBatch.class))).thenAnswer(invocation -> {
            FinanceCostBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(10L);
            return batch;
        });
        when(projectRepository.findAll()).thenReturn(List.of(projectA, projectB));
        doNothing().when(participationService).ensureCurrentMemberHistories(any());
        when(participationHistoryRepository.findByProject_ProjectId("P-A")).thenReturn(List.of(historyA));
        when(participationHistoryRepository.findByProject_ProjectId("P-B")).thenReturn(List.of(historyB));
        when(ventureProfileRepository.findByProject_ProjectId("P-A")).thenReturn(Optional.of(ventureA));
        when(ventureProfileRepository.findByProject_ProjectId("P-B")).thenReturn(Optional.of(ventureB));

        when(projectMemberRepository.findByProjectIdInWithUser(any())).thenReturn(List.of(
                SysProjectMember.builder().projectId("P-A").user(user).weight(0).build(),
                SysProjectMember.builder().projectId("P-B").user(user).weight(0).build()
        ));
        when(batchProjectCostControlRepository.findAllById(any())).thenReturn(List.of(
                BatchProjectCostControl.builder().projectId("P-A").enabled(true).priority(0).build(),
                BatchProjectCostControl.builder().projectId("P-B").enabled(false).priority(0).build()
        ));

        when(costEntryRepository.existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(any(), any(), any())).thenReturn(false);
        when(costSummaryRepository.findByProject_ProjectIdAndLedgerMonth(any(), any())).thenReturn(Optional.empty());

        financeCostBatchService.runBatch("2026-03");

        ArgumentCaptor<List<FinanceCostEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(costEntryRepository).saveAll(entriesCaptor.capture());
        List<FinanceCostEntry> savedEntries = entriesCaptor.getValue();

        assertEquals(31, savedEntries.size());
        assertEquals(new BigDecimal("300.00"), savedEntries.get(0).getFinalSettlementCost());
        assertEquals("P-A", savedEntries.get(0).getProject().getProjectId());
    }

    @Test
    void shouldHandleSingleProject() {
        User user = User.builder().userId("U-1").name("Alice").dailyWage(new BigDecimal("300.00")).build();

        SysProject projectA = SysProject.builder()
                .projectId("P-A").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();

        FinanceVentureProfile ventureA = FinanceVentureProfile.builder().legacyVentureId(1L).project(projectA).displayName("VA").build();

        Instant dayStart = java.time.LocalDate.parse("2026-03-01").atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();

        ProjectMemberParticipationHistory historyA = ProjectMemberParticipationHistory.builder()
                .project(projectA).user(user).joinedAt(dayStart).leftAt(null).build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.empty());
        when(costBatchRepository.save(any(FinanceCostBatch.class))).thenAnswer(invocation -> {
            FinanceCostBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(10L);
            return batch;
        });
        when(projectRepository.findAll()).thenReturn(List.of(projectA));
        doNothing().when(participationService).ensureCurrentMemberHistories(any());
        when(participationHistoryRepository.findByProject_ProjectId("P-A")).thenReturn(List.of(historyA));
        when(ventureProfileRepository.findByProject_ProjectId("P-A")).thenReturn(Optional.of(ventureA));

        when(projectMemberRepository.findByProjectIdInWithUser(any())).thenReturn(List.of(
                SysProjectMember.builder().projectId("P-A").user(user).weight(0).build()
        ));
        when(batchProjectCostControlRepository.findAllById(any())).thenReturn(List.of(
                BatchProjectCostControl.builder().projectId("P-A").enabled(true).priority(0).build()
        ));

        when(costEntryRepository.existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(any(), any(), any())).thenReturn(false);
        when(costSummaryRepository.findByProject_ProjectIdAndLedgerMonth(any(), any())).thenReturn(Optional.empty());

        financeCostBatchService.runBatch("2026-03");

        ArgumentCaptor<List<FinanceCostEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(costEntryRepository).saveAll(entriesCaptor.capture());
        List<FinanceCostEntry> savedEntries = entriesCaptor.getValue();

        assertEquals(31, savedEntries.size());
        assertEquals(new BigDecimal("300.00"), savedEntries.get(0).getFinalSettlementCost());
        assertEquals(new BigDecimal("300.00"), savedEntries.get(0).getLaborCost());
    }

    @Test
    void shouldTreatNullWeightAsZero() {
        User user = User.builder().userId("U-1").name("Alice").dailyWage(new BigDecimal("300.00")).build();

        SysProject projectA = SysProject.builder()
                .projectId("P-A").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();
        SysProject projectB = SysProject.builder()
                .projectId("P-B").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();

        FinanceVentureProfile ventureA = FinanceVentureProfile.builder().legacyVentureId(1L).project(projectA).displayName("VA").build();
        FinanceVentureProfile ventureB = FinanceVentureProfile.builder().legacyVentureId(2L).project(projectB).displayName("VB").build();

        Instant dayStart = java.time.LocalDate.parse("2026-03-01").atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();

        ProjectMemberParticipationHistory historyA = ProjectMemberParticipationHistory.builder()
                .project(projectA).user(user).joinedAt(dayStart).leftAt(null).build();
        ProjectMemberParticipationHistory historyB = ProjectMemberParticipationHistory.builder()
                .project(projectB).user(user).joinedAt(dayStart).leftAt(null).build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.empty());
        when(costBatchRepository.save(any(FinanceCostBatch.class))).thenAnswer(invocation -> {
            FinanceCostBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(10L);
            return batch;
        });
        when(projectRepository.findAll()).thenReturn(List.of(projectA, projectB));
        doNothing().when(participationService).ensureCurrentMemberHistories(any());
        when(participationHistoryRepository.findByProject_ProjectId("P-A")).thenReturn(List.of(historyA));
        when(participationHistoryRepository.findByProject_ProjectId("P-B")).thenReturn(List.of(historyB));
        when(ventureProfileRepository.findByProject_ProjectId("P-A")).thenReturn(Optional.of(ventureA));
        when(ventureProfileRepository.findByProject_ProjectId("P-B")).thenReturn(Optional.of(ventureB));

        when(projectMemberRepository.findByProjectIdInWithUser(any())).thenReturn(List.of(
                SysProjectMember.builder().projectId("P-A").user(user).weight(null).build(),
                SysProjectMember.builder().projectId("P-B").user(user).weight(null).build()
        ));
        when(batchProjectCostControlRepository.findAllById(any())).thenReturn(List.of(
                BatchProjectCostControl.builder().projectId("P-A").enabled(true).priority(0).build(),
                BatchProjectCostControl.builder().projectId("P-B").enabled(true).priority(0).build()
        ));

        when(costEntryRepository.existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(any(), any(), any())).thenReturn(false);
        when(costSummaryRepository.findByProject_ProjectIdAndLedgerMonth(any(), any())).thenReturn(Optional.empty());

        financeCostBatchService.runBatch("2026-03");

        ArgumentCaptor<List<FinanceCostEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(costEntryRepository).saveAll(entriesCaptor.capture());
        List<FinanceCostEntry> savedEntries = entriesCaptor.getValue();

        assertEquals(62, savedEntries.size());
        assertTrue(savedEntries.stream().allMatch(e -> e.getFinalSettlementCost().compareTo(new BigDecimal("150.00")) == 0));
    }

    @Test
    void shouldSkipUserWithNoActiveProjects() {
        User user = User.builder().userId("U-1").name("Alice").dailyWage(new BigDecimal("300.00")).build();

        SysProject projectA = SysProject.builder()
                .projectId("P-A").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.COMPLETED)
                .manager(user).cost(BigDecimal.ZERO).build();

        FinanceVentureProfile ventureA = FinanceVentureProfile.builder().legacyVentureId(1L).project(projectA).displayName("VA").build();

        Instant dayStart = java.time.LocalDate.parse("2026-03-01").atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();

        ProjectMemberParticipationHistory historyA = ProjectMemberParticipationHistory.builder()
                .project(projectA).user(user).joinedAt(dayStart).leftAt(null).build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.empty());
        when(costBatchRepository.save(any(FinanceCostBatch.class))).thenAnswer(invocation -> {
            FinanceCostBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(10L);
            return batch;
        });
        when(projectRepository.findAll()).thenReturn(List.of(projectA));
        doNothing().when(participationService).ensureCurrentMemberHistories(any());
        when(participationHistoryRepository.findByProject_ProjectId("P-A")).thenReturn(List.of(historyA));
        when(ventureProfileRepository.findByProject_ProjectId("P-A")).thenReturn(Optional.of(ventureA));

        when(projectMemberRepository.findByProjectIdInWithUser(any())).thenReturn(List.of(
                SysProjectMember.builder().projectId("P-A").user(user).weight(0).build()
        ));
        when(batchProjectCostControlRepository.findAllById(any())).thenReturn(List.of(
                BatchProjectCostControl.builder().projectId("P-A").enabled(true).priority(0).build()
        ));

        when(costSummaryRepository.findByProject_ProjectIdAndLedgerMonth(any(), any())).thenReturn(Optional.empty());

        FinanceCostBatchRunResponse response = financeCostBatchService.runBatch("2026-03");

        assertEquals(0, response.getGeneratedRecordCount());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getTotalSettlementCost());
    }

    @Test
    void runBatch_returnsExistingCompletedBatchForSameMonthInsteadOfCreatingAnotherOne() {
        User manager = User.builder().userId("U-1").name("Alice").build();
        SysProject project = SysProject.builder()
                .projectId("P-1").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(manager).cost(new BigDecimal("0")).build();
        FinanceVentureProfile venture = FinanceVentureProfile.builder()
                .legacyVentureId(101L).project(project).displayName("V-101").build();

        FinanceCostBatch existingBatch = FinanceCostBatch.builder()
                .id(33L)
                .ledgerMonth("2026-03")
                .status(FinanceBatchStatus.COMPLETED)
                .generatedRecordCount(2)
                .completedAt(Instant.parse("2026-03-31T00:00:00Z"))
                .build();
        FinanceCostSummary summary = FinanceCostSummary.builder()
                .project(project)
                .ledgerMonth("2026-03")
                .totalSettlementCost(new BigDecimal("600.00"))
                .entryCount(2)
                .build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.of(existingBatch));
        when(costSummaryRepository.findByLedgerMonth("2026-03")).thenReturn(List.of(summary));
        when(projectRepository.findAll()).thenReturn(List.of(project));

        FinanceCostBatchRunResponse response = financeCostBatchService.runBatch("2026-03");

        assertEquals(33L, response.getBatchId());
        assertEquals(FinanceBatchStatus.COMPLETED, response.getStatus());
        assertEquals(2, response.getGeneratedRecordCount());
        assertEquals(new BigDecimal("600.00"), response.getTotalSettlementCost());
        verify(costBatchRepository, never()).save(any(FinanceCostBatch.class));
        verify(costEntryRepository, never()).saveAll(any());
    }

    @Test
    void runBatch_rejectsExplicitRerunWhenSameMonthBatchIsStillRunning() {
        FinanceCostBatch runningBatch = FinanceCostBatch.builder()
                .id(44L)
                .ledgerMonth("2026-03")
                .status(FinanceBatchStatus.RUNNING)
                .build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.of(runningBatch));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> financeCostBatchService.runBatch("2026-03", true));

        assertEquals("cost batch is already running for ledger month 2026-03", error.getMessage());
        verify(costBatchRepository, never()).save(any(FinanceCostBatch.class));
        verify(costEntryRepository, never()).saveAll(any());
    }

    @Test
    void shouldDefaultDailyWageTo300WhenNull() {
        User user = User.builder().userId("U-1").name("Alice").dailyWage(null).build();

        SysProject project = SysProject.builder()
                .projectId("P-A").flowType(FlowType.PROJECT).projectStatus(ProjectStatus.IMPLEMENTING)
                .manager(user).cost(BigDecimal.ZERO).build();

        FinanceVentureProfile venture = FinanceVentureProfile.builder().legacyVentureId(1L).project(project).displayName("VA").build();

        Instant dayStart = java.time.LocalDate.parse("2026-03-01").atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();

        ProjectMemberParticipationHistory history = ProjectMemberParticipationHistory.builder()
                .project(project).user(user).joinedAt(dayStart).leftAt(null).build();

        when(costBatchRepository.findTopByLedgerMonthOrderByIdDesc("2026-03")).thenReturn(Optional.empty());
        when(costBatchRepository.save(any(FinanceCostBatch.class))).thenAnswer(invocation -> {
            FinanceCostBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(10L);
            return batch;
        });
        when(projectRepository.findAll()).thenReturn(List.of(project));
        doNothing().when(participationService).ensureCurrentMemberHistories(any());
        when(participationHistoryRepository.findByProject_ProjectId("P-A")).thenReturn(List.of(history));
        when(ventureProfileRepository.findByProject_ProjectId("P-A")).thenReturn(Optional.of(venture));

        when(projectMemberRepository.findByProjectIdInWithUser(any())).thenReturn(List.of(
                SysProjectMember.builder().projectId("P-A").user(user).weight(0).build()
        ));
        when(batchProjectCostControlRepository.findAllById(any())).thenReturn(List.of(
                BatchProjectCostControl.builder().projectId("P-A").enabled(true).priority(0).build()
        ));

        when(costEntryRepository.existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(any(), any(), any())).thenReturn(false);
        when(costSummaryRepository.findByProject_ProjectIdAndLedgerMonth(any(), any())).thenReturn(Optional.empty());

        financeCostBatchService.runBatch("2026-03");

        ArgumentCaptor<List<FinanceCostEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(costEntryRepository).saveAll(entriesCaptor.capture());
        List<FinanceCostEntry> savedEntries = entriesCaptor.getValue();

        assertEquals(31, savedEntries.size());
        assertEquals(new BigDecimal("300.00"), savedEntries.get(0).getDailyWageSnapshot());
        assertEquals(new BigDecimal("300.00"), savedEntries.get(0).getFinalSettlementCost());
    }
}
