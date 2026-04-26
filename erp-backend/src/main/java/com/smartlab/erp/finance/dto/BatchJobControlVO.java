package com.smartlab.erp.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobControlVO {
    private String jobKey;
    private String displayName;
    private Boolean enabled;
    private String scheduleMode;
    private Integer frequencyMinutes;
    private Integer runAtHour;
    private Integer runAtMinute;
    private Instant lastTriggeredAt;
    private String lastTriggeredKey;
    private String lastStatus;
    private String lastMessage;
    private Instant updatedAt;
}
