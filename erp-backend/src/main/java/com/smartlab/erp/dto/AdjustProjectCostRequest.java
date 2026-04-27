package com.smartlab.erp.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdjustProjectCostRequest {
    private String itemName;
    private String type;
    private BigDecimal amount;
}
