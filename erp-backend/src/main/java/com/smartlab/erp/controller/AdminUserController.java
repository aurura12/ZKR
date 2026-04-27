package com.smartlab.erp.controller;

import com.smartlab.erp.dto.ProvisionUserRequest;
import com.smartlab.erp.dto.UpdateDailyWageRequest;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.service.AuthService;
import com.smartlab.erp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthService authService;
    private final UserService userService;

    private void requireProvisionAdmin() {
        if (!authService.canProvisionAccounts(authService.getCurrentUser())) {
            throw new PermissionDeniedException("仅指定管理员可操作");
        }
    }

    @PostMapping("/provision")
    public ResponseEntity<Map<String, String>> provisionUser(@Valid @RequestBody ProvisionUserRequest request) {
        authService.provisionUser(request);
        return ResponseEntity.ok(Map.of("message", "账号创建成功，初始密码为：账号+123"));
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        requireProvisionAdmin();
        return ResponseEntity.ok(userService.findAllUsersIncludingInactive());
    }

    @PutMapping("/{userId}/daily-wage")
    public ResponseEntity<Map<String, String>> updateDailyWage(
            @PathVariable String userId,
            @Valid @RequestBody UpdateDailyWageRequest request) {
        requireProvisionAdmin();
        userService.updateDailyWage(userId, request.getDailyWage());
        return ResponseEntity.ok(Map.of("message", "日工资更新成功"));
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateUser(@PathVariable String userId) {
        requireProvisionAdmin();
        userService.deactivateUser(userId);
        return ResponseEntity.ok(Map.of("message", "用户已离职"));
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<Map<String, String>> activateUser(@PathVariable String userId) {
        requireProvisionAdmin();
        userService.activateUser(userId);
        return ResponseEntity.ok(Map.of("message", "用户已还原为在职"));
    }
}
