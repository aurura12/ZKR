package com.smartlab.erp.meeting.controller;

import com.smartlab.erp.meeting.dto.CreateMeetingRequest;
import com.smartlab.erp.meeting.dto.MeetingResponse;
import com.smartlab.erp.meeting.entity.MeetingRecord;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingController {

    private final TencentMeetingService meetingService;
    private final TencentMeetingWebhookService webhookService;
    private final TencentMeetingUserSyncService userSyncService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createMeeting(
            @RequestBody CreateMeetingRequest request,
            @RequestAttribute("userId") String userId) {
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
     * 手动触发腾讯会议用户同步：拉取腾讯会议用户列表，按手机号匹配钉钉目录，自动建立映射
     */
    @PostMapping("/sync-users")
    public ResponseEntity<Map<String, Object>> syncUsers() {
        TencentMeetingUserSyncService.SyncResult result = userSyncService.syncUsers();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "用户同步完成",
                "data", Map.of(
                        "matched", result.matched(),
                        "unmatched", result.unmatched(),
                        "totalTmUsers", result.totalTmUsers(),
                        "errors", result.errors()
                )
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
