package com.smartlab.erp.service;

import com.smartlab.erp.entity.*;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.repository.*;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.util.AuthUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private static final DateTimeFormatter DEADLINE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final WorkOrderRepository workOrderRepository;
    private final SysProjectRepository projectRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final ProductIdeaDetailRepository productIdeaDetailRepository;
    private final UserRepository userRepository;
    private final InternalMessageService internalMessageService;

    // ── 权限校验：是否是组长（Manager 或 Idea 主理人） ──
    private void assertLeader(String projectId, String currentUserId) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));
        String managerId = project.getManager() == null ? null : project.getManager().getUserId();
        ProductIdeaDetail detail = productIdeaDetailRepository.findByProjectId(projectId).orElse(null);
        String ideaOwnerId = detail != null && detail.getIdeaOwnerUserId() != null && !detail.getIdeaOwnerUserId().isBlank()
                ? detail.getIdeaOwnerUserId()
                : managerId;
        boolean isLeader = currentUserId.equals(managerId) || (ideaOwnerId != null && currentUserId.equals(ideaOwnerId));
        if (!isLeader) {
            throw new PermissionDeniedException("仅组长可执行此操作");
        }
    }

    // ── 查询项目成员并格式化 ──
    private String memberName(String userId) {
        if (userId == null || userId.isBlank()) return "";
        return userRepository.findById(userId)
                .map(u -> u.getName() != null ? u.getName() : u.getUsername())
                .orElse(userId);
    }

    // ── 创建工单 ──
    @Transactional
    public Map<String, Object> createWorkOrder(String projectId, String assigneeId,
                                                String title, String description,
                                                String expectedOutput, Instant deadline,
                                                String currentUserId) {
        assertLeader(projectId, currentUserId);

        // 校验成员存在
        boolean isMember = projectMemberRepository.findByProjectId(projectId).stream()
                .anyMatch(m -> m.getUser() != null && m.getUser().getUserId().equals(assigneeId));
        if (!isMember) {
            throw new BusinessException("执行人必须是当前项目成员");
        }

        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));

        WorkOrder workOrder = WorkOrder.builder()
                .projectId(projectId)
                .creatorId(currentUserId)
                .assigneeId(assigneeId)
                .creatorName(memberName(currentUserId))
                .assigneeName(memberName(assigneeId))
                .title(title.trim())
                .description(description != null ? description.trim() : null)
                .expectedOutput(expectedOutput != null ? expectedOutput.trim() : null)
                .deadline(deadline)
                .status(WorkOrderStatus.PENDING)
                .build();
        workOrderRepository.save(workOrder);

        // 发送通知给执行人
        String deadlineText = deadline == null ? "未设置截止时间" : DEADLINE_FORMATTER.format(deadline);
        internalMessageService.sendMessage(
                assigneeId,
                "WORK_ORDER_ASSIGNED",
                "你收到了新的工单任务",
                "你在项目《" + project.getName() + "》中有新工单：「" + title + "」（截止：" + deadlineText + "）",
                projectId
        );

        return Map.of("success", true, "id", workOrder.getId());
    }

    // ── 查询项目下所有工单 ──
    public List<Map<String, Object>> listWorkOrders(String projectId, String currentUserId) {
        SysProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在: " + projectId));

        List<WorkOrder> orders;
        String managerId = project.getManager() == null ? null : project.getManager().getUserId();
        ProductIdeaDetail detail = productIdeaDetailRepository.findByProjectId(projectId).orElse(null);
        String ideaOwnerId = detail != null && detail.getIdeaOwnerUserId() != null && !detail.getIdeaOwnerUserId().isBlank()
                ? detail.getIdeaOwnerUserId() : managerId;
        boolean isLeader = currentUserId.equals(managerId) || (ideaOwnerId != null && currentUserId.equals(ideaOwnerId));

        if (isLeader) {
            orders = workOrderRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        } else {
            orders = workOrderRepository.findByProjectIdAndAssigneeIdOrderByCreatedAtDesc(projectId, currentUserId);
        }

        return orders.stream().map(this::toMap).collect(Collectors.toList());
    }

    // ── 工单统计 ──
    public Map<String, Object> getWorkOrderStats(String projectId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pending", workOrderRepository.countByProjectIdAndStatus(projectId, WorkOrderStatus.PENDING));
        stats.put("inProgress", workOrderRepository.countByProjectIdAndStatus(projectId, WorkOrderStatus.IN_PROGRESS));
        stats.put("completed", workOrderRepository.countByProjectIdAndStatus(projectId, WorkOrderStatus.COMPLETED));
        stats.put("closed", workOrderRepository.countByProjectIdAndStatus(projectId, WorkOrderStatus.CLOSED));
        return stats;
    }

    // ── 状态流转 ──

    @Transactional
    public Map<String, Object> acceptWorkOrder(Long workOrderId, String currentUserId) {
        WorkOrder order = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new BusinessException("工单不存在"));
        if (!order.getAssigneeId().equals(currentUserId)) {
            throw new PermissionDeniedException("仅执行人可接单");
        }
        if (order.getStatus() != WorkOrderStatus.PENDING) {
            throw new BusinessException("仅待处理状态的工单可接单");
        }
        order.setStatus(WorkOrderStatus.IN_PROGRESS);
        workOrderRepository.save(order);

        // 通知组长
        String projectName = projectRepository.findById(order.getProjectId())
                .map(SysProject::getName).orElse("项目");
        internalMessageService.sendMessage(
                order.getCreatorId(),
                "WORK_ORDER_ASSIGNED",
                "工单已被接单",
                "成员「" + order.getAssigneeName() + "」已接单：「" + order.getTitle() + "」（项目：" + projectName + "）",
                order.getProjectId()
        );
        return Map.of("success", true);
    }

    @Transactional
    public Map<String, Object> submitWorkOrder(Long workOrderId, String currentUserId) {
        WorkOrder order = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new BusinessException("工单不存在"));
        if (!order.getAssigneeId().equals(currentUserId)) {
            throw new PermissionDeniedException("仅执行人可提交完成");
        }
        if (order.getStatus() != WorkOrderStatus.IN_PROGRESS) {
            throw new BusinessException("仅进行中状态的工单可提交完成");
        }
        order.setStatus(WorkOrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        workOrderRepository.save(order);

        // 通知组长
        String projectName = projectRepository.findById(order.getProjectId())
                .map(SysProject::getName).orElse("项目");
        internalMessageService.sendMessage(
                order.getCreatorId(),
                "WORK_ORDER_ASSIGNED",
                "工单已完成",
                "成员「" + order.getAssigneeName() + "」已完成工单：「" + order.getTitle() + "」（项目：" + projectName + "）",
                order.getProjectId()
        );
        return Map.of("success", true);
    }

    @Transactional
    public Map<String, Object> confirmWorkOrder(Long workOrderId, String currentUserId) {
        WorkOrder order = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new BusinessException("工单不存在"));
        assertLeader(order.getProjectId(), currentUserId);
        if (order.getStatus() != WorkOrderStatus.COMPLETED) {
            throw new BusinessException("仅已完成状态的工单可确认关闭");
        }
        order.setStatus(WorkOrderStatus.CLOSED);
        workOrderRepository.save(order);

        // 通知执行人
        String projectName = projectRepository.findById(order.getProjectId())
                .map(SysProject::getName).orElse("项目");
        internalMessageService.sendMessage(
                order.getAssigneeId(),
                "WORK_ORDER_ASSIGNED",
                "工单已确认",
                "组长已确认你完成的工单：「" + order.getTitle() + "」（项目：" + projectName + "）",
                order.getProjectId()
        );
        return Map.of("success", true);
    }

    // ── 取消工单（仅组长可操作） ──
    @Transactional
    public Map<String, Object> cancelWorkOrder(Long workOrderId, String currentUserId) {
        WorkOrder order = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new BusinessException("工单不存在"));
        assertLeader(order.getProjectId(), currentUserId);
        order.setStatus(WorkOrderStatus.CANCELLED);
        workOrderRepository.save(order);

        // 通知执行人
        String projectName = projectRepository.findById(order.getProjectId())
                .map(SysProject::getName).orElse("项目");
        internalMessageService.sendMessage(
                order.getAssigneeId(),
                "WORK_ORDER_ASSIGNED",
                "工单已被取消",
                "组长已取消工单：「" + order.getTitle() + "」（项目：" + projectName + "）",
                order.getProjectId()
        );
        return Map.of("success", true);
    }

    // ── 转为 Map 响应 ──
    private Map<String, Object> toMap(WorkOrder order) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", order.getId());
        map.put("projectId", order.getProjectId());
        map.put("creatorId", order.getCreatorId());
        map.put("assigneeId", order.getAssigneeId());
        map.put("creatorName", order.getCreatorName());
        map.put("assigneeName", order.getAssigneeName());
        map.put("title", order.getTitle());
        map.put("description", order.getDescription());
        map.put("expectedOutput", order.getExpectedOutput());
        map.put("deadline", order.getDeadline());
        map.put("status", order.getStatus().name());
        map.put("createdAt", order.getCreatedAt());
        map.put("completedAt", order.getCompletedAt());
        return map;
    }
}
