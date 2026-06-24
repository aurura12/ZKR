package com.smartlab.erp.controller;

import com.smartlab.erp.dto.InternalMessageResponse;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.service.InternalMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class InternalMessageController {

    private final InternalMessageService internalMessageService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<InternalMessageResponse>> list(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(internalMessageService.listCurrentUserMessages(currentUser.getId()));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> unreadCount(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(Map.of("count", internalMessageService.unreadCount(currentUser.getId())));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id,
                                                         @AuthenticationPrincipal UserPrincipal currentUser) {
        internalMessageService.markAsRead(id, currentUser.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> markAllRead(@AuthenticationPrincipal UserPrincipal currentUser) {
        int count = internalMessageService.markAllAsRead(currentUser.getId());
        return ResponseEntity.ok(Map.of("success", true, "count", count));
    }
}
