package com.smartlab.erp.ocr;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class ContractOcrResult {

    boolean success;

    String contractNo;
    String company;
    String signingEntity;
    String counterparty;
    String description;
    String signType;
    LocalDate signDate;
    BigDecimal contractAmount;
    String currency;
    String paymentMethod;
    LocalDate startDate;
    LocalDate endDate;
    LocalDate collectionDate;
    String status;
    BigDecimal collectedAmount;
    BigDecimal uncollectedAmount;
    String invoiceStatus;
    BigDecimal invoiceAmount;
    String responsiblePerson;
    String archiveNo;
    String remarks;

    BigDecimal confidence;

    String error;

    public static ContractOcrResult failed(String error) {
        return ContractOcrResult.builder()
                .success(false)
                .error(error)
                .build();
    }
}
