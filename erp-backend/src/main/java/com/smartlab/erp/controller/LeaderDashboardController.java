package com.smartlab.erp.controller;

import com.smartlab.erp.dto.LeaderDashboardResponse;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.service.LeaderDashboardService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/leader")
@RequiredArgsConstructor
//@CrossOrigin(origins = "*", allowCredentials = "true")
public class LeaderDashboardController {

    private final LeaderDashboardService leaderDashboardService;

    /**
     * 获取队长工作台数据
     */
    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaderDashboardResponse> getLeaderDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam String role) {

        LeaderDashboardResponse response = leaderDashboardService.getLeaderDashboard(
                currentUser.getUserId(),
                role
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 检查当前用户是否是某角色的队长
     */
    @GetMapping("/check-leader")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> checkLeaderStatus(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam String role) {

        boolean isLeader = leaderDashboardService.isLeader(currentUser.getUserId(), role);

        Map<String, Object> result = new HashMap<>();
        result.put("isLeader", isLeader);
        result.put("userId", currentUser.getUserId());
        result.put("role", role.toUpperCase());

        return ResponseEntity.ok(result);
    }

    /**
     * 为用户分配角色（管理员操作）
     */
    @PostMapping("/assign-role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRole(@RequestBody AssignRoleRequest request) {
        leaderDashboardService.assignRoleToUser(
                request.getUserId(),
                request.getRole(),
                request.getIsLeader()
        );
        return ResponseEntity.ok().build();
    }

    /**
     * 移除用户角色（管理员操作）
     */
    @DeleteMapping("/remove-role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeRole(@RequestBody RemoveRoleRequest request) {
        leaderDashboardService.removeRoleFromUser(request.getUserId(), request.getRole());
        return ResponseEntity.ok().build();
    }

    @Data
    public static class AssignRoleRequest {
        private String userId;
        private String role;
        private Boolean isLeader = false;
    }

    @Data
    public static class RemoveRoleRequest {
        private String userId;
        private String role;
    }
}
