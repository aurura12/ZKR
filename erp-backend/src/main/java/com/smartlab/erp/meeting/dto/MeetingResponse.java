package com.smartlab.erp.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingResponse {

    private Long id;

    private String meetingId;

    private String topic;

    private String description;

    private LocalDateTime startTime;

    private Integer duration;

    private String joinUrl;

    private String password;

    private String status;

    private String creatorId;

    private String creatorName;

    private String projectId;

    private String projectName;

    private String recordingUrl;

    private List<ParticipantInfo> participants;

    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantInfo {
        private String userId;
        private String userName;
        private String attendStatus;
    }
}
