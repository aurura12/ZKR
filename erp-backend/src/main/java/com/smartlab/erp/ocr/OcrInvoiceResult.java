package com.smartlab.erp.ocr;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class OcrInvoiceResult {

    boolean success;

    String invoiceNumber;

    LocalDate invoiceDate;

    BigDecimal amountInclTax;

    BigDecimal amountExTax;

    BigDecimal taxAmount;

    String taxRate;

    String sellerName;

    String buyerName;

    String items;

    String rawText;

    BigDecimal confidence;

    String error;

    public static OcrInvoiceResult failed(String error) {
        return OcrInvoiceResult.builder()
                .success(false)
                .error(error)
                .build();
    }
}
