package com.smartlab.erp.controller;

import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    // ── 创建工单 ──
    @PostMapping("/work-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> createWorkOrder(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        String projectId = (String) body.get("projectId");
        String assigneeId = (String) body.get("assigneeId");
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        String expectedOutput = (String) body.get("expectedOutput");
        Object deadlineObj = body.get("deadline");
        Instant deadline = null;
        if (deadlineObj instanceof String) {
            deadline = Instant.parse((String) deadlineObj);
        } else if (deadlineObj instanceof Number) {
            deadline = Instant.ofEpochMilli(((Number) deadlineObj).longValue());
        }
        return ResponseEntity.ok(
                workOrderService.createWorkOrder(projectId, assigneeId, title, description, expectedOutput, deadline, currentUser.getId())
        );
    }

    // ── 查询项目下工单 ──
    @GetMapping("/projects/{projectId}/work-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> listWorkOrders(
            @PathVariable String projectId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(workOrderService.listWorkOrders(projectId, currentUser.getId()));
    }

    // ── 工单统计 ──
    @GetMapping("/projects/{projectId}/work-orders/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getWorkOrderStats(@PathVariable String projectId) {
        return ResponseEntity.ok(workOrderService.getWorkOrderStats(projectId));
    }

    // ── 接单 PENDING → IN_PROGRESS ──
    @PatchMapping("/work-orders/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> acceptWorkOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(workOrderService.acceptWorkOrder(id, currentUser.getId()));
    }

    // ── 提交完成 IN_PROGRESS → COMPLETED ──
    @PatchMapping("/work-orders/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> submitWorkOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(workOrderService.submitWorkOrder(id, currentUser.getId()));
    }

    // ── 确认关闭 COMPLETED → CLOSED ──
    @PatchMapping("/work-orders/{id}/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> confirmWorkOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(workOrderService.confirmWorkOrder(id, currentUser.getId()));
    }

    // ── 取消工单 ──
    @PostMapping("/work-orders/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> cancelWorkOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(workOrderService.cancelWorkOrder(id, currentUser.getId()));
    }
}
