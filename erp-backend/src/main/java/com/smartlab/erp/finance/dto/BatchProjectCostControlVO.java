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
public class BatchProjectCostControlVO {
    private String projectId;
    private String projectName;
    private Boolean enabled;
    private Integer priority;
    private String note;
    private Instant updatedAt;
}
