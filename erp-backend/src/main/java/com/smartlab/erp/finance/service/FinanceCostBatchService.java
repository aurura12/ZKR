package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectStatus;
import com.smartlab.erp.entity.ProjectMemberParticipationHistory;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.finance.dto.FinanceCostBatchRunResponse;
import com.smartlab.erp.finance.dto.FinanceCostPreviewItem;
import com.smartlab.erp.finance.dto.FinanceCostPreviewResponse;
import com.smartlab.erp.finance.dto.FinanceVentureRef;
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
import com.smartlab.erp.finance.support.FinanceAmounts;
import com.smartlab.erp.repository.ProjectMemberParticipationHistoryRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.service.ProjectMemberParticipationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceCostBatchService {

    private static final Pattern LEDGER_MONTH_PATTERN = Pattern.compile("\\d{4}-\\d{2}");
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("8.00");
    private static final BigDecimal WEIGHTED_MULTIPLIER = new BigDecimal("1.75");
    private static final BigDecimal UNWEIGHTED_MULTIPLIER = new BigDecimal("1.25");
    private static final BigDecimal DEFAULT_DAILY_WAGE = new BigDecimal("300.00");

    private final FinanceCostBatchRepository costBatchRepository;
    private final FinanceCostEntryRepository costEntryRepository;
    private final FinanceCostSummaryRepository costSummaryRepository;
    private final FinanceVentureProfileRepository ventureProfileRepository;
    private final ProjectMemberParticipationHistoryRepository participationHistoryRepository;
    private final ProjectMemberParticipationService participationService;
    private final SysProjectMemberRepository projectMemberRepository;
    private final BatchProjectCostControlRepository batchProjectCostControlRepository;
    private final SysProjectRepository projectRepository;

    @Transactional
    public FinanceCostBatchRunResponse runBatch(String ledgerMonth) {
        cleanupBusinessRoleEntries();
        return runBatch(ledgerMonth, false);
    }

    @Transactional
    public void cleanupBusinessRoleEntries() {
        int deleted = costEntryRepository.deleteByUserRoleBusinessOrBd();
        if (deleted > 0) {
            log.info("删除 BUSINESS/BD 角色用户在成本跑批中的所有记录，共 {} 条", deleted);
        }
    }

    private static final LocalDate SHANGHAI_TODAY = LocalDate.now(SHANGHAI_ZONE);

    @Transactional
    public FinanceCostBatchRunResponse runBatch(String ledgerMonth, boolean rerunExistingMonth) {
        validateLedgerMonth(ledgerMonth);

        LocalDate yesterday = LocalDate.now(SHANGHAI_ZONE).minusDays(1);
        String expectedMonth = YearMonth.from(yesterday).toString();
        if (!ledgerMonth.equals(expectedMonth)) {
            throw new IllegalArgumentException("跑批仅支持上月账期: " + expectedMonth + "，传入: " + ledgerMonth);
        }

        String batchKey = ledgerMonth + "-" + yesterday.toString();

        FinanceCostBatch existingBatch = costBatchRepository.findTopByLedgerMonthOrderByIdDesc(ledgerMonth)
                .orElse(null);
        if (existingBatch != null && existingBatch.getStatus() == FinanceBatchStatus.RUNNING) {
            throw new IllegalArgumentException("cost batch is already running for ledger month " + ledgerMonth);
        }
        if (existingBatch != null && FinanceBatchStatus.COMPLETED == existingBatch.getStatus()
                && yesterday.toString().equals(existingBatch.getRemark())) {
            return buildRunResponse(existingBatch, true);
        }

        FinanceCostBatch batch = costBatchRepository.save(FinanceCostBatch.builder()
                .ledgerMonth(ledgerMonth)
                .status(FinanceBatchStatus.RUNNING)
                .batchDate(yesterday)
                .startedAt(Instant.now())
                .remark(yesterday.toString())
                .build());

        List<SysProject> projects = loadAccrualProjects();
        LocalDate accrualDate = yesterday;

        Set<String> projectIds = projects.stream().map(SysProject::getProjectId).collect(Collectors.toSet());
        Map<String, Integer> projectWeightsByUserProject = loadProjectMemberWeights(projectIds);
        Map<String, Boolean> costControlEnabled = loadCostControlEnabled(projectIds);

        try {
            for (SysProject project : projects) {
                participationService.ensureCurrentMemberHistories(project.getProjectId());
            }

            Map<String, List<ProjectMemberParticipationHistory>> historiesByProject = new LinkedHashMap<>();
            for (SysProject project : projects) {
                historiesByProject.put(project.getProjectId(),
                        participationHistoryRepository.findByProject_ProjectId(project.getProjectId()));
            }

            Map<String, Long> sourceIdByProject = new HashMap<>();
            Map<String, Instant> endInstantByProject = new HashMap<>();
            for (SysProject project : projects) {
                sourceIdByProject.put(project.getProjectId(), resolveSourceId(project));
                endInstantByProject.put(project.getProjectId(), resolveProjectEndInstant(project));
            }

            List<FinanceCostEntry> dayEntries = buildDailyEntriesCrossProject(
                    projects, historiesByProject, costControlEnabled, projectWeightsByUserProject,
                    batch, ledgerMonth, accrualDate, sourceIdByProject, endInstantByProject);

            int generatedRecordCount = dayEntries.size();
            BigDecimal totalSettlementCost = dayEntries.stream()
                    .map(FinanceCostEntry::getFinalSettlementCost)
                    .reduce(BigDecimal.ZERO, FinanceAmounts::add);

            if (!dayEntries.isEmpty()) {
                costEntryRepository.saveAll(dayEntries);
            }

            Map<String, List<FinanceCostEntry>> entriesByProject = dayEntries.stream()
                    .collect(Collectors.groupingBy(e -> e.getProject().getProjectId()));

            for (SysProject project : projects) {
                String pid = project.getProjectId();
                List<FinanceCostEntry> projectEntries = entriesByProject.getOrDefault(pid, List.of());
                List<FinanceCostEntry> monthlyEntries = projectEntries.isEmpty()
                        ? selectEffectiveMonthlyEntries(pid, ledgerMonth)
                        : projectEntries;

                BigDecimal totalLaborCost = monthlyEntries.stream()
                        .map(FinanceCostEntry::getLaborCost)
                        .reduce(BigDecimal.ZERO, FinanceAmounts::add);
                BigDecimal totalProjectSettlementCost = monthlyEntries.stream()
                        .map(FinanceCostEntry::getFinalSettlementCost)
                        .reduce(BigDecimal.ZERO, FinanceAmounts::add);

                FinanceCostSummary summary = costSummaryRepository.findByProject_ProjectIdAndLedgerMonth(pid, ledgerMonth)
                        .orElseGet(FinanceCostSummary::new);
                summary.setBatch(batch);
                summary.setProject(project);
                summary.setLedgerMonth(ledgerMonth);
                summary.setTotalLaborCost(FinanceAmounts.scale(totalLaborCost));
                summary.setTotalMiddlewareFee(BigDecimal.ZERO.setScale(2));
                summary.setTotalSettlementCost(FinanceAmounts.scale(totalProjectSettlementCost));
                summary.setEntryCount(monthlyEntries.size());
                costSummaryRepository.save(summary);
            }

            batch.setGeneratedRecordCount(generatedRecordCount);
            batch.setCompletedAt(Instant.now());
            batch.setStatus(FinanceBatchStatus.COMPLETED);
            costBatchRepository.save(batch);

            return FinanceCostBatchRunResponse.builder()
                    .batchId(batch.getId())
                    .ledgerMonth(batch.getLedgerMonth())
                    .status(batch.getStatus())
                    .ventureCount(projects.size())
                    .generatedRecordCount(generatedRecordCount)
                    .totalSettlementCost(FinanceAmounts.scale(totalSettlementCost))
                    .reusedExistingBatch(false)
                    .build();
        } catch (RuntimeException ex) {
            batch.setStatus(FinanceBatchStatus.FAILED);
            batch.setRemark("FAILED:" + yesterday + ":" + ex.getMessage());
            costBatchRepository.save(batch);
            throw ex;
        }
    }

    private FinanceCostBatchRunResponse buildRunResponse(FinanceCostBatch batch, boolean reusedExistingBatch) {
        List<FinanceCostSummary> summaries = costSummaryRepository.findByLedgerMonth(batch.getLedgerMonth());
        BigDecimal totalSettlementCost = summaries.stream()
                .map(FinanceCostSummary::getTotalSettlementCost)
                .reduce(BigDecimal.ZERO, FinanceAmounts::add);
        int ventureCount = loadAccrualProjects().size();

        return FinanceCostBatchRunResponse.builder()
                .batchId(batch.getId())
                .ledgerMonth(batch.getLedgerMonth())
                .status(batch.getStatus())
                .ventureCount(ventureCount)
                .generatedRecordCount(batch.getGeneratedRecordCount())
                .totalSettlementCost(FinanceAmounts.scale(totalSettlementCost))
                .reusedExistingBatch(reusedExistingBatch)
                .build();
    }

    private List<SysProject> loadAccrualProjects() {
        return projectRepository.findAll().stream()
                .filter(this::isProjectFlow)
                .sorted(Comparator.comparing(SysProject::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SysProject::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean isProjectFlow(SysProject project) {
        return project != null && project.getFlowType() == FlowType.PROJECT;
    }

    private Instant resolveProjectEndInstant(SysProject project) {
        if (project == null) {
            return null;
        }
        if (project.getEndDate() != null) {
            return project.getEndDate();
        }
        if (project.getFlowType() == null || project.getFlowType() == FlowType.PROJECT) {
            return project.getProjectStatus() == ProjectStatus.COMPLETED ? project.getUpdatedAt() : null;
        }
        if (project.getFlowType() == FlowType.PRODUCT) {
            return project.getProductStatus() == com.smartlab.erp.entity.ProductStatus.LAUNCHED
                    || project.getProductStatus() == com.smartlab.erp.entity.ProductStatus.SHELVED
                    ? project.getUpdatedAt()
                    : null;
        }
        if (project.getFlowType() == FlowType.RESEARCH) {
            return project.getResearchStatus() == com.smartlab.erp.entity.ResearchStatus.ARCHIVE
                    || project.getResearchStatus() == com.smartlab.erp.entity.ResearchStatus.SHELVED
                    ? project.getUpdatedAt()
                    : null;
        }
        return null;
    }

    private Long resolveSourceId(SysProject project) {
        return ventureProfileRepository.findByProject_ProjectId(project.getProjectId())
                .map(FinanceVentureProfile::getLegacyVentureId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public FinanceCostPreviewResponse preview(Long ventureId, String ledgerMonth) {
        validateLedgerMonth(ledgerMonth);
        FinanceVentureProfile venture = ventureProfileRepository.findByLegacyVentureId(ventureId)
                .orElseThrow(() -> new IllegalArgumentException("Finance venture not found: " + ventureId));

        List<FinanceCostEntry> entries = costEntryRepository.findByProject_ProjectIdAndLedgerMonth(
                venture.getProject().getProjectId(), ledgerMonth);

        boolean hasDailyEntries = entries.stream().anyMatch(entry -> entry.getAccrualDate() != null);
        List<FinanceCostEntry> effectiveEntries = hasDailyEntries
                ? entries.stream().filter(entry -> entry.getAccrualDate() != null).toList()
                : entries;

        Map<String, PreviewAccumulator> grouped = new LinkedHashMap<>();
        for (FinanceCostEntry entry : effectiveEntries) {
            if (entry.getUser() == null || entry.getUser().getUserId() == null) {
                continue;
            }
            String userId = entry.getUser().getUserId();
            PreviewAccumulator accumulator = grouped.computeIfAbsent(userId, id -> new PreviewAccumulator(entry.getUser()));
            accumulator.add(entry.getWorkHours(), entry.getLaborCost(), entry.getFinalSettlementCost());
        }

        List<FinanceCostPreviewItem> items = grouped.values().stream()
                .sorted(Comparator.comparing(acc -> acc.user().getUserId()))
                .map(acc -> FinanceCostPreviewItem.builder()
                        .userId(acc.user().getUserId())
                        .userName(acc.user().getName())
                        .role(acc.user().getRole())
                        .workHours(FinanceAmounts.scale(acc.workHours()))
                        .laborCost(FinanceAmounts.scale(acc.laborCost()))
                        .finalSettlementCost(FinanceAmounts.scale(acc.finalSettlementCost()))
                        .build())
                .toList();

        BigDecimal totalSettlementCost = effectiveEntries.stream()
                .map(FinanceCostEntry::getFinalSettlementCost)
                .reduce(BigDecimal.ZERO, FinanceAmounts::add);
        FinanceCostBatch batch = costBatchRepository.findTopByLedgerMonthOrderByIdDesc(ledgerMonth).orElse(null);

        return FinanceCostPreviewResponse.builder()
                .venture(toVentureRef(venture))
                .ledgerMonth(ledgerMonth)
                .batchId(batch == null ? null : batch.getId())
                .batchStatus(batch == null ? null : batch.getStatus())
                .entryCount(items.size())
                .totalSettlementCost(FinanceAmounts.scale(totalSettlementCost))
                .items(items)
                .build();
    }

    private boolean isActiveProject(SysProject project) {
        if (project == null) {
            return false;
        }
        if (project.getFlowType() == null || project.getFlowType() == FlowType.PROJECT) {
            return project.getProjectStatus() != ProjectStatus.COMPLETED;
        }
        if (project.getFlowType() == FlowType.PRODUCT) {
            return project.getProductStatus() != null && project.getProductStatus() != com.smartlab.erp.entity.ProductStatus.SHELVED;
        }
        if (project.getFlowType() == FlowType.RESEARCH) {
            return project.getResearchStatus() != null
                    && project.getResearchStatus() != com.smartlab.erp.entity.ResearchStatus.ARCHIVE
                    && project.getResearchStatus() != com.smartlab.erp.entity.ResearchStatus.SHELVED;
        }
        return true;
    }

    private List<FinanceCostEntry> selectEffectiveMonthlyEntries(String projectId, String ledgerMonth) {
        List<FinanceCostEntry> entries = costEntryRepository.findByProject_ProjectIdAndLedgerMonth(projectId, ledgerMonth);
        List<FinanceCostEntry> dailyEntries = entries.stream()
                .filter(entry -> entry.getAccrualDate() != null)
                .toList();
        return dailyEntries.isEmpty() ? entries : dailyEntries;
    }

    private List<FinanceCostEntry> buildDailyEntriesCrossProject(
            List<SysProject> projects,
            Map<String, List<ProjectMemberParticipationHistory>> historiesByProject,
            Map<String, Boolean> costControlEnabled,
            Map<String, Integer> projectWeightsByUserProject,
            FinanceCostBatch batch,
            String ledgerMonth,
            LocalDate accrualDate,
            Map<String, Long> sourceIdByProject,
            Map<String, Instant> endInstantByProject) {

        Instant dayStart = accrualDate.atStartOfDay(SHANGHAI_ZONE).toInstant();
        Instant dayEnd = accrualDate.plusDays(1).atStartOfDay(SHANGHAI_ZONE).toInstant();

        Map<String, UserProjectOverlaps> userOverlapsByUser = new LinkedHashMap<>();

        for (SysProject project : projects) {
            String projectId = project.getProjectId();
            List<ProjectMemberParticipationHistory> histories = historiesByProject.getOrDefault(projectId, List.of());
            Instant projectEndInstant = endInstantByProject.get(projectId);
            Instant effectiveDayEnd = projectEndInstant != null && projectEndInstant.isBefore(dayEnd) ? projectEndInstant : dayEnd;

            if (!effectiveDayEnd.isAfter(dayStart)) {
                continue;
            }

            for (ProjectMemberParticipationHistory history : histories) {
                if (history == null || history.getUser() == null || history.getUser().getUserId() == null || history.getJoinedAt() == null) {
                    continue;
                }
                Instant joinedAt = history.getJoinedAt();
                Instant leftAt = history.getLeftAt();
                Instant overlapStart = joinedAt.isAfter(dayStart) ? joinedAt : dayStart;
                Instant overlapEnd = leftAt == null || leftAt.isAfter(effectiveDayEnd) ? effectiveDayEnd : leftAt;
                if (!overlapEnd.isAfter(overlapStart)) {
                    continue;
                }

                long seconds = java.time.Duration.between(overlapStart, overlapEnd).getSeconds();
                String userId = history.getUser().getUserId();

                UserProjectOverlaps overlaps = userOverlapsByUser.computeIfAbsent(userId,
                        id -> new UserProjectOverlaps(history.getUser()));
                overlaps.addOverlap(projectId, seconds);
            }
        }

        List<FinanceCostEntry> entries = new ArrayList<>();

        for (UserProjectOverlaps overlaps : userOverlapsByUser.values()) {
            User user = overlaps.user;

            int activeProjectCount = 0;
            for (String pid : overlaps.projectOverlaps.keySet()) {
                if (isActiveAndCostControlled(pid, projects, costControlEnabled)) {
                    activeProjectCount++;
                }
            }

            if (activeProjectCount == 0) {
                continue;
            }

            BigDecimal dailyWage = resolveDailyWage(user);
            if (dailyWage == null) {
                continue;
            }
            BigDecimal dailyShare = FinanceAmounts.scale(dailyWage.divide(BigDecimal.valueOf(activeProjectCount), 2, RoundingMode.HALF_UP));

            for (Map.Entry<String, Long> overlapEntry : overlaps.projectOverlaps.entrySet()) {
                String pid = overlapEntry.getKey();
                long seconds = overlapEntry.getValue();

                if (!isActiveAndCostControlled(pid, projects, costControlEnabled)) {
                    continue;
                }

                String userId = user.getUserId();
                if (costEntryRepository.existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(pid, userId, accrualDate)) {
                    continue;
                }

                SysProject project = findProjectById(projects, pid);
                if (project == null) {
                    continue;
                }

                BigDecimal workHours = FinanceAmounts.scale(
                        BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP));

                String userProjectKey = pid + ":" + userId;
                Integer weight = projectWeightsByUserProject.get(userProjectKey);
                BigDecimal weightMultiplier = (weight != null && weight > 0) ? WEIGHTED_MULTIPLIER : UNWEIGHTED_MULTIPLIER;

                BigDecimal laborCost = FinanceAmounts.scale(dailyShare.multiply(weightMultiplier));
                BigDecimal finalSettlementCost = laborCost;

                Long sourceId = sourceIdByProject.get(pid);

                entries.add(FinanceCostEntry.builder()
                        .batch(batch)
                        .project(project)
                        .user(user)
                        .ledgerMonth(ledgerMonth)
                        .accrualDate(accrualDate)
                        .workHours(workHours)
                        .dailyWageSnapshot(dailyWage)
                        .laborCost(laborCost)
                        .middlewareRoyaltyFee(BigDecimal.ZERO)
                        .finalSettlementCost(finalSettlementCost)
                        .sourceTable("sys_project")
                        .sourceId(sourceId)
                        .build());
            }
        }

        return entries;
    }

    private boolean isActiveAndCostControlled(String projectId, List<SysProject> projects,
                                               Map<String, Boolean> costControlEnabled) {
        SysProject project = findProjectById(projects, projectId);
        if (project == null) {
            return false;
        }
        if (!isActiveProject(project)) {
            return false;
        }
        return costControlEnabled.getOrDefault(projectId, true);
    }

    private SysProject findProjectById(List<SysProject> projects, String projectId) {
        for (SysProject p : projects) {
            if (p.getProjectId().equals(projectId)) {
                return p;
            }
        }
        return null;
    }

    private Map<String, Integer> loadProjectMemberWeights(Set<String> projectIds) {
        Map<String, Integer> result = new HashMap<>();
        if (projectIds.isEmpty()) {
            return result;
        }
        List<SysProjectMember> members = projectMemberRepository.findByProjectIdInWithUser(new ArrayList<>(projectIds));
        for (SysProjectMember member : members) {
            if (member.getProjectId() == null || member.getUser() == null || member.getUser().getUserId() == null) {
                continue;
            }
            String key = member.getProjectId() + ":" + member.getUser().getUserId();
            result.put(key, member.getWeight());
        }
        return result;
    }

    private Map<String, Boolean> loadCostControlEnabled(Set<String> projectIds) {
        Map<String, Boolean> result = new HashMap<>();
        if (projectIds.isEmpty()) {
            return result;
        }
        List<BatchProjectCostControl> controls = batchProjectCostControlRepository.findAllById(projectIds);
        for (BatchProjectCostControl control : controls) {
            result.put(control.getProjectId(), control.getEnabled());
        }
        return result;
    }

    private BigDecimal resolveDailyWage(User user) {
        if (user == null || user.getDailyWage() == null || user.getDailyWage().compareTo(BigDecimal.ZERO) < 0) {
            return DEFAULT_DAILY_WAGE;
        }
        if (isBusinessRole(user.getRole())) {
            return null;
        }
        return FinanceAmounts.scale(user.getDailyWage());
    }

    private static boolean isBusinessRole(String role) {
        if (role == null) return false;
        String r = role.trim().toUpperCase();
        return r.equals("BUSINESS") || r.equals("BD");
    }

    private static final class UserProjectOverlaps {
        private final User user;
        private final Map<String, Long> projectOverlaps = new LinkedHashMap<>();

        private UserProjectOverlaps(User user) {
            this.user = user;
        }

        private void addOverlap(String projectId, long seconds) {
            projectOverlaps.merge(projectId, Math.max(0L, seconds), Long::sum);
        }
    }

    private static final class PreviewAccumulator {
        private final User user;
        private BigDecimal workHours = BigDecimal.ZERO;
        private BigDecimal laborCost = BigDecimal.ZERO;
        private BigDecimal finalSettlementCost = BigDecimal.ZERO;

        private PreviewAccumulator(User user) {
            this.user = user;
        }

        private void add(BigDecimal workHours, BigDecimal laborCost, BigDecimal finalSettlementCost) {
            this.workHours = FinanceAmounts.add(this.workHours, workHours);
            this.laborCost = FinanceAmounts.add(this.laborCost, laborCost);
            this.finalSettlementCost = FinanceAmounts.add(this.finalSettlementCost, finalSettlementCost);
        }

        private User user() {
            return user;
        }

        private BigDecimal workHours() {
            return workHours;
        }

        private BigDecimal laborCost() {
            return laborCost;
        }

        private BigDecimal finalSettlementCost() {
            return finalSettlementCost;
        }
    }

    private List<MemberAllocation> buildAllocations(SysProject project) {
        List<SysProjectMember> members = projectMemberRepository.findByProjectId(project.getProjectId());
        if (members.isEmpty()) {
            return List.of(new MemberAllocation(project.getManager(), FinanceAmounts.scale(project.getCost())));
        }

        BigDecimal totalCost = FinanceAmounts.scale(project.getCost());
        int totalWeight = members.stream()
                .mapToInt(member -> member.getWeight() != null && member.getWeight() > 0 ? member.getWeight() : 1)
                .sum();

        List<MemberAllocation> allocations = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < members.size(); i++) {
            SysProjectMember member = members.get(i);
            BigDecimal amount;
            if (i == members.size() - 1) {
                amount = FinanceAmounts.scale(totalCost.subtract(allocated));
            } else {
                int weight = member.getWeight() != null && member.getWeight() > 0 ? member.getWeight() : 1;
                amount = FinanceAmounts.scale(totalCost.multiply(BigDecimal.valueOf(weight))
                        .divide(BigDecimal.valueOf(totalWeight), 2, java.math.RoundingMode.HALF_UP));
                allocated = FinanceAmounts.add(allocated, amount);
            }
            allocations.add(new MemberAllocation(member.getUser(), amount));
        }
        return allocations;
    }

    private void validateLedgerMonth(String ledgerMonth) {
        if (ledgerMonth == null || !LEDGER_MONTH_PATTERN.matcher(ledgerMonth).matches()) {
            throw new IllegalArgumentException("ledgerMonth must match YYYY-MM");
        }
    }

    private FinanceVentureRef toVentureRef(FinanceVentureProfile venture) {
        return FinanceVentureRef.builder()
                .projectId(venture.getProject().getProjectId())
                .legacyVentureId(venture.getLegacyVentureId())
                .displayName(venture.getDisplayName())
                .legacyStage(venture.getLegacyStage())
                .build();
    }

    private record MemberAllocation(User user, BigDecimal allocatedBaseCost) {
    }
}
