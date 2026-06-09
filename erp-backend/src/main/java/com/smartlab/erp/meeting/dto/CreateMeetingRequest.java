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
public class CreateMeetingRequest {

    private String topic;

    private String description;

    private LocalDateTime startTime;

    private Integer duration;

    private String password;

    private String projectId;

    private List<String> participantIds;
}
