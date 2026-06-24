package com.smartlab.erp.meeting.controller;

import com.smartlab.erp.meeting.dto.CreateMeetingRequest;
import com.smartlab.erp.meeting.dto.MeetingResponse;
import com.smartlab.erp.meeting.entity.MeetingRecord;
import com.smartlab.erp.meeting.entity.TencentUserMapping;
import com.smartlab.erp.meeting.repository.TencentUserMappingRepository;
import com.smartlab.erp.meeting.service.TencentMeetingService;
import com.smartlab.erp.meeting.service.TencentMeetingUserSyncService;
import com.smartlab.erp.meeting.service.TencentMeetingWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingController {

    private final TencentMeetingService meetingService;
    private final TencentMeetingWebhookService webhookService;
    private final TencentMeetingUserSyncService userSyncService;
    private final TencentUserMappingRepository mappingRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createMeeting(
            @RequestBody CreateMeetingRequest request,
            @RequestAttribute("userId") String userId) {
        // 权限校验：只有 can_create = true 的用户才能创建会议
        Optional<TencentUserMapping> mappingOpt = mappingRepository.findByErpUserId(userId);
        if (mappingOpt.isEmpty() || !Boolean.TRUE.equals(mappingOpt.get().getCanCreate())) {
            log.warn("[TencentMeeting] 用户 {} 无创建会议权限", userId);
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "当前用户没有创建会议的权限"
            ));
        }
        MeetingRecord record = meetingService.createMeeting(request, userId);
        MeetingResponse response = meetingService.convertToResponse(record);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "会议创建成功",
                "data", response
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMeetingList(
            @RequestParam(required = false) String creatorId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String status) {
        List<MeetingRecord> records = meetingService.getMeetingList(creatorId, projectId, status);
        List<MeetingResponse> responses = records.stream()
                .map(meetingService::convertToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", responses
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMeetingDetail(@PathVariable Long id) {
        MeetingRecord record = meetingService.getMeetingDetail(id);
        MeetingResponse response = meetingService.convertToResponse(record);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelMeeting(@PathVariable Long id) {
        meetingService.cancelMeeting(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "会议已取消"
        ));
    }

    @PutMapping("/{id}/end")
    public ResponseEntity<Map<String, Object>> endMeeting(@PathVariable Long id) {
        meetingService.endMeeting(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "会议已结束"
        ));
    }

    /**
     * 获取已映射的腾讯会议用户列表（用于参会人选单）
     */
    @GetMapping("/mapped-users")
    public ResponseEntity<Map<String, Object>> getMappedUsers() {
        List<Map<String, String>> users = userSyncService.getMappedUsers();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", users
        ));
    }

    /**
     * 直接从腾讯会议拉取所有用户（创建会议时选择参会人）
     */
    @GetMapping("/tm-users")
    public ResponseEntity<Map<String, Object>> listTmUsers() {
        List<Map<String, String>> users = userSyncService.listAllTmUsers();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", users
        ));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String body,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            HttpServletRequest request) {
        log.info("[TencentMeeting] Webhook received from IP: {}", request.getRemoteAddr());
        webhookService.handleWebhookEvent(body, signature);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
