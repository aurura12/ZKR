package com.smartlab.erp.service;

import com.smartlab.erp.dto.InternalMessageResponse;
import com.smartlab.erp.entity.InternalMessage;
import com.smartlab.erp.repository.InternalMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalMessageService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final InternalMessageRepository internalMessageRepository;

    @Transactional
    public void sendMessage(String recipientUserId, String messageType, String title, String content, String projectId) {
        internalMessageRepository.save(InternalMessage.builder()
                .recipientUserId(recipientUserId)
                .messageType(messageType)
                .title(title)
                .content(content)
                .projectId(projectId)
                .build());
    }

    @Transactional(readOnly = true)
    public List<InternalMessageResponse> listCurrentUserMessages(String userId) {
        return internalMessageRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId).stream()
                .map(message -> InternalMessageResponse.builder()
                        .id(message.getId())
                        .messageType(message.getMessageType())
                        .title(message.getTitle())
                        .content(message.getContent())
                        .projectId(message.getProjectId())
                        .read(Boolean.TRUE.equals(message.getRead()))
                        .createdAt(FORMATTER.format(message.getCreatedAt()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return internalMessageRepository.countByRecipientUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long id, String userId) {
        internalMessageRepository.findById(id)
                .filter(message -> userId.equals(message.getRecipientUserId()))
                .ifPresent(message -> {
                    message.setRead(true);
                    internalMessageRepository.save(message);
                });
    }

    @Transactional
    public int markAllAsRead(String userId) {
        return internalMessageRepository.markAllReadByUserId(userId);
    }
}
