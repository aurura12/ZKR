package com.smartlab.erp.controller;

import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.service.UserService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口 - 已适配 UserPrincipal
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true") // 显式支持前端跨域
public class UserController {

    private final UserService userService;

    /**
     * 获取人才库列表
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<User>> getAllUsers(@AuthenticationPrincipal UserPrincipal currentUser) {
        AccountDomain accountDomain = currentUser == null ? AccountDomain.ERP : currentUser.getAccountDomain();
        return ResponseEntity.ok(userService.findAllUsers(accountDomain));
    }

    /**
     * ✅ 更新用户头像 - 修复类型不匹配导致的 403 问题
     */
    @PutMapping("/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> updateAvatar(
            // 🟢 这里必须使用 UserPrincipal，因为 Filter 里塞的是这个类型
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody UpdateAvatarRequest request
    ) {
        // 健壮性检查
        if (currentUser == null) {
            System.err.println(">>> [ERROR] @AuthenticationPrincipal 注入失败，currentUser 为空！");
            return ResponseEntity.status(401).build();
        }

        System.out.println(">>> [DEBUG] 正在为用户 " + currentUser.getUserId() + " 更新头像: " + request.getAvatar());

        userService.updateAvatar(currentUser.getUserId(), request.getAvatar());
        return ResponseEntity.ok().build();
    }

    /**
     * 更新用户头像的请求体
     */
    @Data
    public static class UpdateAvatarRequest {
        @NotBlank(message = "头像地址不能为空")
        private String avatar;
    }
}
