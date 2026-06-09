package com.smartlab.erp.meeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.meeting.config.TencentMeetingConfig;
import com.smartlab.erp.meeting.dto.CreateMeetingRequest;
import com.smartlab.erp.meeting.dto.MeetingResponse;
import com.smartlab.erp.meeting.entity.MeetingParticipant;
import com.smartlab.erp.meeting.entity.MeetingRecord;
import com.smartlab.erp.meeting.entity.TencentUserMapping;
import com.smartlab.erp.meeting.exception.TencentMeetingException;
import com.smartlab.erp.meeting.repository.MeetingParticipantRepository;
import com.smartlab.erp.meeting.repository.MeetingRecordRepository;
import com.smartlab.erp.meeting.repository.TencentUserMappingRepository;
import com.smartlab.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TencentMeetingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private final TencentMeetingConfig config;
    private final TencentMeetingSignatureService signatureService;
    private final MeetingRecordRepository meetingRecordRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final UserRepository userRepository;
    private final TencentUserMappingRepository tencentUserMappingRepository;

    @Transactional
    public MeetingRecord createMeeting(CreateMeetingRequest request, String creatorId) {
        try {
            // 1. 构建腾讯会议请求（V1 API 参数格式）
            long startTimestamp = request.getStartTime().atZone(SHANGHAI_ZONE).toEpochSecond();
            int durationMinutes = request.getDuration() != null ? request.getDuration() : 60;
            long endTimestamp = startTimestamp + durationMinutes * 60L;

            Map<String, Object> payload = new LinkedHashMap<>();
            // 通过映射表查询腾讯会议 userid，若未配置则使用 ERP userId 兜底
            String tencentUserId = tencentUserMappingRepository.findByErpUserId(creatorId)
                    .map(TencentUserMapping::getTencentUserId)
                    .orElse(creatorId);
            payload.put("userid", tencentUserId);
            payload.put("instanceid", 1);
            payload.put("subject", request.getTopic());
            payload.put("type", 0);
            payload.put("start_time", String.valueOf(startTimestamp));
            payload.put("end_time", String.valueOf(endTimestamp));

            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                payload.put("password", request.getPassword());
            }
            if (request.getDescription() != null) {
                payload.put("description", request.getDescription());
            }

            // 2. 调用腾讯会议 API
            JsonNode responseNode = invokeMeetingApi("创建会议", "/v1/meetings", payload, false);

            // 3. 解析响应
            String meetingId = responseNode.path("meeting_id").asText();
            String joinUrl = responseNode.path("join_url").asText();
            String password = responseNode.has("password") ? responseNode.path("password").asText() : request.getPassword();

            // 4. 保存到本地数据库
            MeetingRecord record = MeetingRecord.builder()
                    .meetingId(meetingId)
                    .topic(request.getTopic())
                    .description(request.getDescription())
                    .startTime(request.getStartTime())
                    .duration(request.getDuration())
                    .joinUrl(joinUrl)
                    .password(password)
                    .status("SCHEDULED")
                    .creatorId(creatorId)
                    .projectId(request.getProjectId())
                    .build();
            meetingRecordRepository.save(record);

            // 5. 保存参会人
            if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
                for (String userId : request.getParticipantIds()) {
                    meetingParticipantRepository.save(MeetingParticipant.builder()
                            .meetingRecordId(record.getId())
                            .userId(userId)
                            .attendStatus("PENDING")
                            .build());
                }
            }

            log.info("[TencentMeeting] Meeting created: meetingId={}, topic={}", meetingId, request.getTopic());
            return record;

        } catch (TencentMeetingException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TencentMeeting] Failed to create meeting", e);
            throw new TencentMeetingException("创建会议", "创建会议失败: " + e.getMessage(), null, null, e);
        }
    }

    public List<MeetingRecord> getMeetingList(String creatorId, String projectId, String status) {
        if (creatorId != null) {
            return meetingRecordRepository.findByCreatorIdOrderByStartTimeDesc(creatorId);
        }
        if (projectId != null) {
            return meetingRecordRepository.findByProjectIdOrderByStartTimeDesc(projectId);
        }
        if (status != null) {
            return meetingRecordRepository.findByStatusOrderByStartTimeDesc(status);
        }
        return meetingRecordRepository.findAll();
    }

    public MeetingRecord getMeetingDetail(Long id) {
        return meetingRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("会议不存在: " + id));
    }

    public MeetingRecord getMeetingByMeetingId(String meetingId) {
        return meetingRecordRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new RuntimeException("会议不存在: meetingId=" + meetingId));
    }

    @Transactional
    public void cancelMeeting(Long id) {
        MeetingRecord record = getMeetingDetail(id);
        record.setStatus("CANCELLED");
        meetingRecordRepository.save(record);
        log.info("[TencentMeeting] Meeting cancelled: id={}", id);
    }

    @Transactional
    public void endMeeting(Long id) {
        MeetingRecord record = getMeetingDetail(id);
        record.setStatus("ENDED");
        meetingRecordRepository.save(record);
        log.info("[TencentMeeting] Meeting ended: id={}", id);
    }

    @Transactional
    public void updateMeetingStatus(String meetingId, String status) {
        MeetingRecord record = meetingRecordRepository.findByMeetingId(meetingId)
                .orElse(null);
        if (record != null) {
            record.setStatus(status);
            meetingRecordRepository.save(record);
            log.info("[TencentMeeting] Meeting status updated: meetingId={}, status={}", meetingId, status);
        }
    }

    @Transactional
    public void updateRecordingUrl(String meetingId, String recordingUrl) {
        MeetingRecord record = meetingRecordRepository.findByMeetingId(meetingId)
                .orElse(null);
        if (record != null) {
            record.setRecordingUrl(recordingUrl);
            meetingRecordRepository.save(record);
            log.info("[TencentMeeting] Recording URL updated: meetingId={}", meetingId);
        }
    }

    private JsonNode invokeMeetingApi(String stage, String url, Map<String, Object> payload, boolean retryAllowed) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            String body = MAPPER.writeValueAsString(payload);

            // 生成公共 Header
            Map<String, String> headers = signatureService.generateCommonHeaders();

            // 计算签名
            String signature = signatureService.calculateSignature("POST", url, body, headers);

            // DEBUG: 打印请求信息
            log.info("[TencentMeeting] === DEBUG REQUEST ===");
            log.info("[TencentMeeting] URL: {}{}", config.getApiBaseUrl(), url);
            log.info("[TencentMeeting] AppId: {}", headers.get("AppId"));
            log.info("[TencentMeeting] SdkId: {}", headers.getOrDefault("SdkId", "(not set)"));
            log.info("[TencentMeeting] X-TC-Key: {}", headers.get("X-TC-Key"));
            log.info("[TencentMeeting] X-TC-Timestamp: {}", headers.get("X-TC-Timestamp"));
            log.info("[TencentMeeting] X-TC-Nonce: {}", headers.get("X-TC-Nonce"));
            log.info("[TencentMeeting] Body: {}", body);
            log.info("[TencentMeeting] === END DEBUG ===");

            // 构建请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiBaseUrl() + url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("AppId", headers.get("AppId"))
                    .header("X-TC-Key", headers.get("X-TC-Key"))
                    .header("X-TC-Timestamp", headers.get("X-TC-Timestamp"))
                    .header("X-TC-Nonce", headers.get("X-TC-Nonce"))
                    .header("X-TC-Signature", signature)
                    .header("X-TC-Registered", "1");

            if (headers.containsKey("SdkId")) {
                requestBuilder.header("SdkId", headers.get("SdkId"));
            }

            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // DEBUG: 打印响应
            log.info("[TencentMeeting] === RESPONSE DEBUG ===");
            log.info("[TencentMeeting] Status Code: {}", response.statusCode());
            log.info("[TencentMeeting] Response Body: {}", response.body());
            log.info("[TencentMeeting] === END RESPONSE DEBUG ===");

            JsonNode root = MAPPER.readTree(response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errmsg = root.has("error_message") ? root.path("error_message").asText() : response.body();
                throw new TencentMeetingException(stage, stage + "失败: " + errmsg, response.statusCode(), response.body());
            }

            return root;

        } catch (TencentMeetingException e) {
            throw e;
        } catch (Exception e) {
            throw new TencentMeetingException(stage, stage + "失败: " + e.getMessage(), null, null, e);
        }
    }

    public MeetingResponse convertToResponse(MeetingRecord record) {
        List<MeetingParticipant> participants = meetingParticipantRepository.findByMeetingRecordId(record.getId());

        String creatorName = userRepository.findById(record.getCreatorId())
                .map(User::getName)
                .orElse("未知");

        List<MeetingResponse.ParticipantInfo> participantInfos = participants.stream()
                .map(p -> {
                    String userName = userRepository.findById(p.getUserId())
                            .map(User::getName)
                            .orElse("未知用户");
                    return MeetingResponse.ParticipantInfo.builder()
                            .userId(p.getUserId())
                            .userName(userName)
                            .attendStatus(p.getAttendStatus())
                            .build();
                })
                .collect(Collectors.toList());

        return MeetingResponse.builder()
                .id(record.getId())
                .meetingId(record.getMeetingId())
                .topic(record.getTopic())
                .description(record.getDescription())
                .startTime(record.getStartTime())
                .duration(record.getDuration())
                .joinUrl(record.getJoinUrl())
                .password(record.getPassword())
                .status(record.getStatus())
                .creatorId(record.getCreatorId())
                .creatorName(creatorName)
                .projectId(record.getProjectId())
                .recordingUrl(record.getRecordingUrl())
                .participants(participantInfos)
                .createdAt(record.getCreatedAt() != null
                        ? LocalDateTime.ofInstant(record.getCreatedAt(), SHANGHAI_ZONE)
                        : null)
                .build();
    }
}
