package com.smartlab.erp.meeting.controller;

import com.smartlab.erp.meeting.entity.TencentUserMapping;
import com.smartlab.erp.meeting.repository.TencentUserMappingRepository;
import com.smartlab.erp.meeting.service.TencentMeetingUserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meetings/mapping")
@RequiredArgsConstructor
@Slf4j
public class TencentUserMappingController {

    private final TencentUserMappingRepository mappingRepository;
    private final TencentMeetingUserSyncService userSyncService;

    /**
     * 获取当前用户的绑定状态
     */
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyMapping(
            @RequestAttribute("userId") String userId) {
        var opt = mappingRepository.findByErpUserId(userId);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "bound", false,
                            "erpUserId", userId,
                            "canCreate", false
                    )
            ));
        }
        TencentUserMapping mapping = opt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bound", true);
        data.put("erpUserId", mapping.getErpUserId());
        data.put("tencentUserId", mapping.getTencentUserId());
        data.put("tencentUsername", mapping.getTencentUsername());
        data.put("canCreate", mapping.getCanCreate());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
        ));
    }

    /**
     * 当前用户绑定腾讯会议账号
     */
    @PostMapping("/bind")
    public ResponseEntity<Map<String, Object>> bindMapping(
            @RequestBody Map<String, String> body,
            @RequestAttribute("userId") String userId) {
        String tencentUserId = body.get("tencentUserId");
        if (tencentUserId == null || tencentUserId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "tencentUserId 不能为空"
            ));
        }

        // 拉取腾讯会议用户列表（后续校验存在性、获取用户名和账号类型都用同一份数据）
        List<Map<String, String>> tmUsers = userSyncService.listAllTmUsers();
        var tmUserOpt = tmUsers.stream()
                .filter(u -> tencentUserId.equals(u.get("userId")))
                .findFirst();

        if (tmUserOpt.isEmpty()) {
            log.warn("[TencentMeeting] 绑定失败，腾讯会议用户 {} 不存在", tencentUserId);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "该腾讯会议账号不存在，请检查输入的 userid 是否正确"
            ));
        }

        Map<String, String> tmUser = tmUserOpt.get();
        String tencentUsername = tmUser.getOrDefault("name", "");
        int accountType = Integer.parseInt(tmUser.getOrDefault("accountType", "0"));
        boolean isPremium = (accountType == 9);

        // 检查这个 tencentUserId 是否已被其他人绑定
        var existing = mappingRepository.findByTencentUserId(tencentUserId);
        if (existing.isPresent() && !existing.get().getErpUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "该腾讯会议账号已被其他用户绑定"
            ));
        }

        // 保存或更新映射
        TencentUserMapping mapping = mappingRepository.findByErpUserId(userId)
                .orElse(TencentUserMapping.builder()
                        .erpUserId(userId)
                        .build());
        mapping.setTencentUserId(tencentUserId);
        mapping.setTencentUsername(tencentUsername);
        mapping.setCanCreate(isPremium);
        mapping.setRemark("用户手动绑定" + (isPremium ? "（高级账号自动开通权限）" : ""));
        mapping.setUpdatedAt(Instant.now());
        mappingRepository.save(mapping);

        log.info("[TencentMeeting] 用户 {} 绑定腾讯会议账号 {} ({}) canCreate={}", userId, tencentUserId, tencentUsername, isPremium);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "绑定成功",
                "data", Map.of(
                        "tencentUserId", tencentUserId,
                        "tencentUsername", tencentUsername
                )
        ));
    }

    /**
     * 当前用户解绑腾讯会议账号
     */
    @PostMapping("/unbind")
    public ResponseEntity<Map<String, Object>> unbindMapping(
            @RequestAttribute("userId") String userId) {
        var opt = mappingRepository.findByErpUserId(userId);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "您尚未绑定腾讯会议账号"
            ));
        }
        mappingRepository.delete(opt.get());
        log.info("[TencentMeeting] 用户 {} 解绑腾讯会议账号", userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "解绑成功"
        ));
    }
}
