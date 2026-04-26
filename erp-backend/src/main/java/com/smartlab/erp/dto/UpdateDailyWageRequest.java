package com.smartlab.erp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateDailyWageRequest {
    @NotNull(message = "日工资不能为空")
    private BigDecimal dailyWage;
}
