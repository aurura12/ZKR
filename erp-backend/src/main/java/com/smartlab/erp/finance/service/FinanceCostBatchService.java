package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.AttendanceRecord;
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
import com.smartlab.erp.repository.AttendanceRecordRepository;
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
    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.333");
    private static final BigDecimal OVERTIME_HOURS = new BigDecimal("11");
    private static final BigDecimal MIN_WORK_HOURS = new BigDecimal("8");
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
    private final AttendanceRecordRepository attendanceRecordRepository;

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
    public FinanceCostBatchRunResponse rebuildMonth(String ledgerMonth) {
        cleanupBusinessRoleEntries();
        if (ledgerMonth == null || !LEDGER_MONTH_PATTERN.matcher(ledgerMonth).matches()) {
            throw new IllegalArgumentException("ledgerMonth must match YYYY-MM");
        }

        costEntryRepository.deleteByLedgerMonth(ledgerMonth);
        costSummaryRepository.deleteByLedgerMonth(ledgerMonth);

        YearMonth ym = YearMonth.parse(ledgerMonth);
        List<LocalDate> days = new ArrayList<>();
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            days.add(d);
        }

        List<SysProject> projects = loadAccrualProjects();
        Set<String> projectIds = projects.stream().map(SysProject::getProjectId).collect(Collectors.toSet());
        Map<String, Integer> projectWeightsByUserProject = loadProjectMemberWeights(projectIds);
        Map<String, Boolean> costControlEnabled = loadCostControlEnabled(projectIds);

        FinanceCostBatch batch = costBatchRepository.save(FinanceCostBatch.builder()
                .ledgerMonth(ledgerMonth)
                .status(FinanceBatchStatus.RUNNING)
                .batchDate(last)
                .startedAt(Instant.now())
                .remark("rebuild-" + ledgerMonth)
                .build());

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

            int totalRecords = 0;
            BigDecimal totalSettlement = BigDecimal.ZERO;

            for (LocalDate accrualDate : days) {
                List<FinanceCostEntry> dayEntries = buildDailyEntriesCrossProject(
                        projects, historiesByProject, costControlEnabled, projectWeightsByUserProject,
                        batch, ledgerMonth, accrualDate, sourceIdByProject, endInstantByProject);

                if (!dayEntries.isEmpty()) {
                    costEntryRepository.saveAll(dayEntries);
                    totalRecords += dayEntries.size();
                    totalSettlement = dayEntries.stream()
                            .map(FinanceCostEntry::getFinalSettlementCost)
                            .reduce(totalSettlement, FinanceAmounts::add);
                }
            }

            for (SysProject project : projects) {
                String pid = project.getProjectId();
                List<FinanceCostEntry> monthlyEntries = selectEffectiveMonthlyEntries(pid, ledgerMonth);
                BigDecimal totalLaborCost = monthlyEntries.stream()
                        .map(FinanceCostEntry::getLaborCost)
                        .reduce(BigDecimal.ZERO, FinanceAmounts::add);
                BigDecimal totalSettlementCost = monthlyEntries.stream()
                        .map(FinanceCostEntry::getFinalSettlementCost)
                        .reduce(BigDecimal.ZERO, FinanceAmounts::add);

                FinanceCostSummary summary = costSummaryRepository.findByProject_ProjectIdAndLedgerMonth(pid, ledgerMonth)
                        .orElseGet(FinanceCostSummary::new);
                summary.setBatch(batch);
                summary.setProject(project);
                summary.setLedgerMonth(ledgerMonth);
                summary.setTotalLaborCost(FinanceAmounts.scale(totalLaborCost));
                summary.setTotalMiddlewareFee(BigDecimal.ZERO.setScale(2));
                summary.setTotalSettlementCost(FinanceAmounts.scale(totalSettlementCost));
                summary.setEntryCount(monthlyEntries.size());
                costSummaryRepository.save(summary);
            }

            batch.setGeneratedRecordCount(totalRecords);
            batch.setCompletedAt(Instant.now());
            batch.setStatus(FinanceBatchStatus.COMPLETED);
            costBatchRepository.save(batch);

            log.info("Rebuilt month {}: {} records generated across {} projects", ledgerMonth, totalRecords, projects.size());
            return FinanceCostBatchRunResponse.builder()
                    .batchId(batch.getId())
                    .ledgerMonth(batch.getLedgerMonth())
                    .status(batch.getStatus())
                    .ventureCount(projects.size())
                    .generatedRecordCount(totalRecords)
                    .totalSettlementCost(FinanceAmounts.scale(totalSettlement))
                    .reusedExistingBatch(false)
                    .build();
        } catch (RuntimeException ex) {
            batch.setStatus(FinanceBatchStatus.FAILED);
            batch.setRemark("rebuild-" + ledgerMonth + ":" + ex.getMessage());
            costBatchRepository.save(batch);
            throw ex;
        }
    }

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

        List<AttendanceRecord> todayRecords = attendanceRecordRepository.findByWorkDateOrderByUserIdAsc(accrualDate);

        Map<String, List<AttendanceRecord>> recordsByUser = new LinkedHashMap<>();
        for (AttendanceRecord r : todayRecords) {
            recordsByUser.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
        }

        Map<String, Double> workHoursByUser = new LinkedHashMap<>();
        for (Map.Entry<String, List<AttendanceRecord>> entry : recordsByUser.entrySet()) {
            String uid = entry.getKey();
            List<AttendanceRecord> recs = entry.getValue();
            AttendanceRecord onDuty = recs.stream().filter(r -> "OnDuty".equals(r.getCheckType())
                    && r.getUserCheckTime() != null && !"NotSigned".equals(r.getTimeResult())).findFirst().orElse(null);
            AttendanceRecord offDuty = recs.stream().filter(r -> "OffDuty".equals(r.getCheckType())
                    && r.getUserCheckTime() != null && !"NotSigned".equals(r.getTimeResult())).findFirst().orElse(null);
            if (onDuty == null || offDuty == null) continue;
            double hours = java.time.Duration.between(onDuty.getUserCheckTime(), offDuty.getUserCheckTime()).toMinutes() / 60.0;
            if (hours < MIN_WORK_HOURS.doubleValue()) continue;
            workHoursByUser.put(uid, hours);
        }

        Map<String, Boolean> activeUserCache = new HashMap<>();
        Map<String, BigDecimal> dailyWageCache = new HashMap<>();
        Map<String, User> userCache = new HashMap<>();

        Map<String, List<String>> userInProjectCache = new HashMap<>();

        List<FinanceCostEntry> entries = new ArrayList<>();

        for (Map.Entry<String, Double> whEntry : workHoursByUser.entrySet()) {
            String userId = whEntry.getKey();
            double workHours = whEntry.getValue();

            boolean userActive = activeUserCache.computeIfAbsent(userId, uid -> {
                for (List<ProjectMemberParticipationHistory> list : historiesByProject.values()) {
                    for (ProjectMemberParticipationHistory h : list) {
                        if (h != null && h.getUser() != null && uid.equals(h.getUser().getUserId())) {
                            return Boolean.TRUE.equals(h.getUser().getActive());
                        }
                    }
                }
                return false;
            });
            if (!userActive) continue;

            List<String> userProjects = userInProjectCache.computeIfAbsent(userId, uid -> {
                List<String> pids = new ArrayList<>();
                Map<String, User> foundUser = new HashMap<>();
                for (Map.Entry<String, List<ProjectMemberParticipationHistory>> hpEntry : historiesByProject.entrySet()) {
                    String pid = hpEntry.getKey();
                    for (ProjectMemberParticipationHistory h : hpEntry.getValue()) {
                        if (h != null && h.getUser() != null && uid.equals(h.getUser().getUserId())) {
                            Instant leftAt = h.getLeftAt();
                            if (leftAt == null || !leftAt.isBefore(accrualDate.atStartOfDay(SHANGHAI_ZONE).toInstant())) {
                                pids.add(pid);
                                foundUser.putIfAbsent("u", h.getUser());
                            }
                            break;
                        }
                    }
                }
                if (foundUser.containsKey("u")) {
                    userCache.put(uid, foundUser.get("u"));
                }
                return pids;
            });

            if (userProjects.isEmpty()) continue;

            User user = userCache.get(userId);
            BigDecimal dailyWage = dailyWageCache.computeIfAbsent(userId, uid -> resolveDailyWage(user));
            if (dailyWage == null) continue;

            int activeProjectCount = 0;
            for (String pid : userProjects) {
                if (isActiveAndCostControlled(pid, projects, costControlEnabled)) {
                    activeProjectCount++;
                }
            }
            if (activeProjectCount == 0) continue;

            BigDecimal dailyShare = FinanceAmounts.scale(dailyWage.divide(BigDecimal.valueOf(activeProjectCount), 2, RoundingMode.HALF_UP));

            BigDecimal overtimeCoef = workHours >= OVERTIME_HOURS.doubleValue() ? OVERTIME_MULTIPLIER : BigDecimal.ONE;

            for (String pid : userProjects) {
                if (!isActiveAndCostControlled(pid, projects, costControlEnabled)) continue;
                if (costEntryRepository.existsByProject_ProjectIdAndUser_UserIdAndAccrualDate(pid, userId, accrualDate)) continue;

                SysProject project = findProjectById(projects, pid);
                if (project == null) continue;

                String userProjectKey = pid + ":" + userId;
                Integer weight = projectWeightsByUserProject.get(userProjectKey);
                BigDecimal weightCoef = (weight != null && weight > 0) ? WEIGHTED_MULTIPLIER : UNWEIGHTED_MULTIPLIER;

                BigDecimal laborCost = FinanceAmounts.scale(dailyShare.multiply(overtimeCoef).multiply(weightCoef));

                Long sourceId = sourceIdByProject.get(pid);

                entries.add(FinanceCostEntry.builder()
                        .batch(batch)
                        .project(project)
                        .user(user)
                        .ledgerMonth(ledgerMonth)
                        .accrualDate(accrualDate)
                        .workHours(FinanceAmounts.scale(BigDecimal.valueOf(workHours)))
                        .dailyWageSnapshot(dailyWage)
                        .laborCost(laborCost)
                        .middlewareRoyaltyFee(BigDecimal.ZERO)
                        .finalSettlementCost(laborCost)
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
