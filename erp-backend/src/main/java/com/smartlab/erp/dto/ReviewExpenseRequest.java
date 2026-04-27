package com.smartlab.erp.dto;

import lombok.Data;

@Data
public class ReviewExpenseRequest {
    private String action;
    private String reason;
}
