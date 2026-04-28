package com.smartlab.erp.service;

import com.smartlab.erp.dto.ProjectMemberEarningsResponse;
import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectExecutionPlan;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.enums.ProjectTierEnum;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.finance.entity.FinanceCostBatch;
import com.smartlab.erp.finance.entity.FinanceCostSummary;
import com.smartlab.erp.finance.enums.FinanceBatchStatus;
import com.smartlab.erp.finance.repository.FinanceCostBatchRepository;
import com.smartlab.erp.finance.repository.FinanceCostSummaryRepository;
import com.smartlab.erp.finance.support.FinanceAmounts;
import com.smartlab.erp.repository.ProjectExecutionPlanRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectFinancialMetricsService {

    private static final Set<String> BUSINESS_ROLES = Set.of("BUSINESS");
    private static final Set<String> EXECUTION_ROLES = Set.of("DEV", "ALGORITHM", "RESEARCH", "MEMBER", "DEMO_ENG", "DATA", "DATA_ENGINEER", "MANAGER", "ADMIN");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SysProjectRepository projectRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final ProjectExecutionPlanRepository executionPlanRepository;
    private final FinanceCostSummaryRepository costSummaryRepository;
    private final FinanceCostBatchRepository costBatchRepository;

    @Value("${auth.admin-usernames:Zhangqi,guojianwen,jiaomiao}")
    private String adminUsernamesConfig;

    @Transactional(readOnly = true)
    public Map<String, ProjectFinancialSnapshot> getProjectSnapshots(List<SysProject> projects,
                                                                     Map<String, ProjectExecutionPlan> executionPlanByProject) {
        if (projects == null || projects.isEmpty()) {
            return Map.of();
        }

        List<String> projectIds = projects.stream()
                .map(SysProject::getProjectId)
                .filter(Objects::nonNull)
                .toList();

        Map<String, FinanceCostSummary> latestSummaryByProject = new HashMap<>();
        costSummaryRepository.findByProject_ProjectIdIn(projectIds).forEach(summary -> {
            String projectId = summary.getProject() == null ? null : summary.getProject().getProjectId();
            if (projectId == null) {
                return;
            }
            FinanceCostSummary existing = latestSummaryByProject.get(projectId);
            if (existing == null || compareSummaryVersion(summary, existing) > 0) {
                latestSummaryByProject.put(projectId, summary);
            }
        });

        Map<String, ProjectFinancialSnapshot> snapshots = new HashMap<>();
        for (SysProject project : projects) {
            ProjectExecutionPlan plan = executionPlanByProject == null ? null : executionPlanByProject.get(project.getProjectId());
            FinanceCostSummary summary = latestSummaryByProject.get(project.getProjectId());
            snapshots.put(project.getProjectId(), buildSnapshot(project, plan, summary));
        }
        return snapshots;
    }

    @Transactional(readOnly = true)
    public ProjectMemberEarningsResponse getMyProjectEarnings(String projectId, UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new PermissionDeniedException("用户未登录或会话已过期");
        }

        SysProject project = isAdminRole(currentUser.getRole(), currentUser.getUsername())
                ? projectRepository.findById(projectId)
                    .orElseThrow(() -> new PermissionDeniedException("项目不存在或无权限访问"))
                : projectRepository.findProjectByIdAndUser(projectId, currentUser.getId())
                    .orElseThrow(() -> new PermissionDeniedException("项目不存在或无权限访问"));

        if (project.getFlowType() != FlowType.PROJECT) {
            throw new BusinessException("仅项目流支持预计分红测算");
        }

        ProjectExecutionPlan executionPlan = executionPlanRepository.findByProjectId(projectId).orElse(null);
        FinanceCostSummary latestSummary = costSummaryRepository.findTopByProject_ProjectIdOrderByLedgerMonthDescIdDesc(projectId).orElse(null);
        ProjectFinancialSnapshot snapshot = buildSnapshot(project, executionPlan, latestSummary);
        List<SysProjectMember> members = projectMemberRepository.findByProjectIdWithUser(projectId);
        int projectMemberCount = (int) members.stream()
                .filter(member -> member.getUser() != null)
                .count();
        SysProjectMember currentMember = members.stream()
                .filter(member -> member.getUser() != null)
                .filter(member -> currentUser.getId().equals(member.getUser().getUserId()))
                .findFirst()
                .orElse(null);

        PoolSplit split = resolvePoolSplit(snapshot.projectTier());
        String normalizedRole = normalizeRole(currentMember == null || currentMember.getUser() == null ? null : currentMember.getUser().getRole());
        boolean businessMember = isBusinessRole(normalizedRole);
        int responsibilityRatio = currentMember == null ? 0 : combinedResponsibility(currentMember);

        if (businessMember && responsibilityRatio == 0) {
            int participantCount = (int) members.stream()
                    .filter(m -> m.getUser() != null)
                    .map(m -> normalizeRole(m.getUser().getRole()))
                    .filter(this::isBusinessRole)
                    .count();
            BigDecimal poolAmount = calculatePoolAmount(snapshot.remainingProfit(), split.businessRatio());
            BigDecimal shareRatio = participantCount > 0
                    ? HUNDRED.divide(BigDecimal.valueOf(participantCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal predictedAmount = participantCount > 0
                    ? FinanceAmounts.scale(poolAmount.divide(BigDecimal.valueOf(participantCount), 2, RoundingMode.HALF_UP))
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return baseResponse(project, snapshot)
                    .eligible(Boolean.TRUE)
                    .poolType("BUSINESS")
                    .poolLabel("商务池")
                    .poolRatio(split.businessRatio())
                    .poolAmount(poolAmount)
                    .shareRatio(shareRatio)
                    .responsibilityRatio(0)
                    .totalPoolResponsibility(0)
                    .participantCount(participantCount)
                    .projectMemberCount(projectMemberCount)
                    .predictedAmount(predictedAmount)
                    .explanation(participantCount > 0
                            ? String.format("当前项目按 %s 规则划分商务池 %s%%，由 %d 名商务成员均分。", tierLabel(snapshot.projectTier()), formatPercent(split.businessRatio()), participantCount)
                            : String.format("当前项目按 %s 规则预留商务池 %s%%，但尚未识别到商务成员，预计分红暂按 0 计算。", tierLabel(snapshot.projectTier()), formatPercent(split.businessRatio())))
                    .build();
        }

        boolean executionMember = currentMember != null && (responsibilityRatio > 0 || isExecutionRole(normalizedRole) || (businessMember && responsibilityRatio > 0));
        int totalPoolResponsibility = members.stream()
                .filter(this::isExecutionPoolMember)
                .mapToInt(this::combinedResponsibility)
                .filter(value -> value > 0)
                .sum();
        int participantCount = (int) members.stream()
                .filter(this::isExecutionPoolMember)
                .filter(member -> combinedResponsibility(member) > 0)
                .count();

        if (executionMember) {
            BigDecimal poolAmount = calculatePoolAmount(snapshot.remainingProfit(), split.executionRatio());
            BigDecimal shareRatio = (responsibilityRatio > 0 && totalPoolResponsibility > 0)
                    ? FinanceAmounts.scale(BigDecimal.valueOf(responsibilityRatio)
                    .multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(totalPoolResponsibility), 2, RoundingMode.HALF_UP))
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal predictedAmount = (responsibilityRatio > 0 && totalPoolResponsibility > 0)
                    ? FinanceAmounts.scale(poolAmount.multiply(BigDecimal.valueOf(responsibilityRatio))
                    .divide(BigDecimal.valueOf(totalPoolResponsibility), 2, RoundingMode.HALF_UP))
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            String explanation;
            if (split.executionRatio().compareTo(BigDecimal.ZERO) <= 0) {
                explanation = String.format("当前项目按 %s 规则不设置实施/技术分红池，预计分红按 0 计算。", tierLabel(snapshot.projectTier()));
            } else if (totalPoolResponsibility <= 0 || responsibilityRatio <= 0) {
                explanation = String.format("当前项目按 %s 规则划分实施/技术池 %s%%，但尚未给你分配有效权责比，预计分红暂按 0 计算。", tierLabel(snapshot.projectTier()), formatPercent(split.executionRatio()));
            } else {
                explanation = String.format("当前项目按 %s 规则划分实施/技术池 %s%%，你按权责比 %d/%d 参与分配。", tierLabel(snapshot.projectTier()), formatPercent(split.executionRatio()), responsibilityRatio, totalPoolResponsibility);
            }
            return baseResponse(project, snapshot)
                    .eligible(Boolean.TRUE)
                    .poolType("EXECUTION")
                    .poolLabel("实施/技术池")
                    .poolRatio(split.executionRatio())
                    .poolAmount(poolAmount)
                    .shareRatio(shareRatio)
                    .responsibilityRatio(responsibilityRatio)
                    .totalPoolResponsibility(totalPoolResponsibility)
                    .participantCount(participantCount)
                    .projectMemberCount(projectMemberCount)
                    .predictedAmount(predictedAmount)
                    .explanation(explanation)
                    .build();
        }

        return baseResponse(project, snapshot)
                .eligible(Boolean.FALSE)
                .poolType("NONE")
                .poolLabel("未纳入分红池")
                .poolRatio(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .poolAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .shareRatio(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .responsibilityRatio(responsibilityRatio)
                .totalPoolResponsibility(totalPoolResponsibility)
                .participantCount(0)
                .projectMemberCount(projectMemberCount)
                .predictedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .explanation("当前角色未纳入该项目的商务池或实施/技术池，预计分红暂按 0 计算。")
                .build();
    }

    @Transactional(readOnly = true)
    public Instant getLatestCompletedCostBatchAt() {
        return costBatchRepository.findTopByStatusOrderByCompletedAtDescIdDesc(FinanceBatchStatus.COMPLETED)
                .map(FinanceCostBatch::getCompletedAt)
                .orElse(null);
    }

    private ProjectMemberEarningsResponse.ProjectMemberEarningsResponseBuilder baseResponse(SysProject project,
                                                                                            ProjectFinancialSnapshot snapshot) {
        return ProjectMemberEarningsResponse.builder()
                .projectId(project.getProjectId())
                .projectTier(snapshot.projectTier().name())
                .tierLabel(tierLabel(snapshot.projectTier()))
                .estimatedRevenue(snapshot.estimatedRevenue())
                .humanCost(snapshot.humanCost())
                .remainingProfit(snapshot.remainingProfit())
                .lastCostBatchAt(getLatestCompletedCostBatchAt());
    }

    public ProjectFinancialSnapshot buildSnapshot(SysProject project,
                                                  ProjectExecutionPlan executionPlan,
                                                  FinanceCostSummary latestSummary) {
        ProjectTierEnum projectTier = resolveProjectTier(project, executionPlan);
        BigDecimal estimatedRevenue = resolveEstimatedRevenue(project);
        BigDecimal humanCost = FinanceAmounts.scale(project == null ? null : project.getCost());
        BigDecimal remainingProfit = FinanceAmounts.subtract(estimatedRevenue, humanCost);
        if (remainingProfit.compareTo(BigDecimal.ZERO) < 0) {
            remainingProfit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new ProjectFinancialSnapshot(projectTier, estimatedRevenue, humanCost, remainingProfit);
    }

    private BigDecimal resolveEstimatedRevenue(SysProject project) {
        if (project == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal estimatedRevenue = project.getEstimatedRevenue() != null ? project.getEstimatedRevenue() : project.getBudget();
        return FinanceAmounts.scale(estimatedRevenue);
    }

    private ProjectTierEnum resolveProjectTier(SysProject project, ProjectExecutionPlan executionPlan) {
        if (executionPlan != null && executionPlan.getProjectTier() != null) {
            return executionPlan.getProjectTier();
        }
        if (project != null && project.getProjectTier() != null) {
            return project.getProjectTier();
        }
        return ProjectTierEnum.N;
    }

    private BigDecimal calculatePoolAmount(BigDecimal remainingProfit, BigDecimal ratio) {
        if (remainingProfit == null || ratio == null || remainingProfit.compareTo(BigDecimal.ZERO) <= 0 || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return FinanceAmounts.scale(remainingProfit.multiply(ratio).divide(HUNDRED, 2, RoundingMode.HALF_UP));
    }

    private int compareSummaryVersion(FinanceCostSummary left, FinanceCostSummary right) {
        int monthCompare = compareLedgerMonth(left.getLedgerMonth(), right.getLedgerMonth());
        if (monthCompare != 0) {
            return monthCompare;
        }
        return Long.compare(left.getId() == null ? 0L : left.getId(), right.getId() == null ? 0L : right.getId());
    }

    private int compareLedgerMonth(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private boolean isExecutionPoolMember(SysProjectMember member) {
        return member != null && member.getUser() != null
                && combinedResponsibility(member) > 0;
    }

    private boolean isBusinessRole(String normalizedRole) {
        return BUSINESS_ROLES.contains(normalizedRole);
    }

    private boolean isExecutionRole(String normalizedRole) {
        return EXECUTION_ROLES.contains(normalizedRole);
    }

    private int combinedResponsibility(SysProjectMember member) {
        if (member == null) {
            return 0;
        }
        return sanitizeRatio(member.getWeight()) + sanitizeRatio(member.getManagerWeight());
    }

    private int sanitizeRatio(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if ("ALGO".equals(normalized)) {
            return "ALGORITHM";
        }
        if ("BUSNESS".equals(normalized)) {
            return "BUSINESS";
        }
        return normalized;
    }

    private boolean isAdminRole(String role, String username) {
        if (role != null && "ADMIN".equalsIgnoreCase(role.trim())) {
            return true;
        }
        return username != null && getAdminUsernames().contains(username.trim());
    }

    private Set<String> getAdminUsernames() {
        return Set.of(adminUsernamesConfig.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }

    private String tierLabel(ProjectTierEnum tier) {
        return switch (tier) {
            case A -> "A级 / EASY";
            case B -> "B级 / 普通";
            case C -> "C级 / 中等";
            case S -> "S级 / 专家";
            case N -> "N级 / 特殊";
        };
    }

    private String formatPercent(BigDecimal ratio) {
        return FinanceAmounts.scale(ratio).stripTrailingZeros().toPlainString();
    }

    private PoolSplit resolvePoolSplit(ProjectTierEnum tier) {
        return switch (tier) {
            case A -> new PoolSplit(new BigDecimal("30.00"), new BigDecimal("10.00"));
            case B -> new PoolSplit(new BigDecimal("20.00"), new BigDecimal("30.00"));
            case C -> new PoolSplit(new BigDecimal("40.00"), new BigDecimal("40.00"));
            case S -> new PoolSplit(new BigDecimal("40.00"), new BigDecimal("55.00"));
            case N -> new PoolSplit(new BigDecimal("35.00"), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        };
    }

    public record ProjectFinancialSnapshot(ProjectTierEnum projectTier,
                                           BigDecimal estimatedRevenue,
                                           BigDecimal humanCost,
                                           BigDecimal remainingProfit) {
    }

    private record PoolSplit(BigDecimal businessRatio, BigDecimal executionRatio) {
    }
}
