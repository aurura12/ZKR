package com.smartlab.erp.dto;

import com.smartlab.erp.enums.AccountDomain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProvisionUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotNull(message = "账号域不能为空")
    private AccountDomain domain;

    private BigDecimal dailyWage;
    private String idNumber;
    private String phone;
    private String bankName;
    private String bankAccount;
    private String ethnicity;
    private String position;
    private Boolean partTime;
    private String paymentEntity;
}
