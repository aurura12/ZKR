package com.smartlab.erp.dto;

import lombok.Data;

@Data
public class SubmitProjectExpenseRequest {
    private String expenseType;
    private String itemName;
    private String amount;
}
