package com.smartlab.erp.meeting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.meeting.config.TencentMeetingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class TencentMeetingWebhookService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TencentMeetingConfig config;
    private final TencentMeetingService meetingService;

    public void handleWebhookEvent(String body, String signature) {
        try {
            // 1. 验证签名（如果配置了 webhook secret）
            if (config.getWebhookSecret() != null && !config.getWebhookSecret().isBlank()) {
                if (!verifySignature(body, signature)) {
                    log.warn("[TencentMeeting] Webhook signature verification failed");
                    throw new RuntimeException("Webhook signature verification failed");
                }
            }

            // 2. 解析事件
            JsonNode event = MAPPER.readTree(body);
            String eventType = event.path("event").asText();
            String meetingId = event.path("meeting_id").asText();

            log.info("[TencentMeeting] Webhook event received: type={}, meetingId={}", eventType, meetingId);

            // 3. 处理事件
            switch (eventType) {
                case "meeting.started":
                    meetingService.updateMeetingStatus(meetingId, "STARTED");
                    break;

                case "meeting.ended":
                    meetingService.updateMeetingStatus(meetingId, "ENDED");
                    break;

                case "meeting.recording.completed":
                    String recordingUrl = event.path("recording_url").asText();
                    meetingService.updateRecordingUrl(meetingId, recordingUrl);
                    break;

                case "meeting.participant.joined":
                case "meeting.participant.left":
                    // 可扩展：更新参会人状态
                    log.info("[TencentMeeting] Participant event: {} for meeting {}", eventType, meetingId);
                    break;

                default:
                    log.info("[TencentMeeting] Unhandled webhook event: {}", eventType);
            }

        } catch (JsonProcessingException e) {
            log.error("[TencentMeeting] Failed to parse webhook JSON", e);
            throw new RuntimeException("Webhook JSON parse error", e);
        } catch (Exception e) {
            log.error("[TencentMeeting] Failed to handle webhook event", e);
            throw e;
        }
    }

    private boolean verifySignature(String body, String signature) {
        try {
            String secret = config.getWebhookSecret();
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(hash);
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("[TencentMeeting] Signature verification error", e);
            return false;
        }
    }
}
