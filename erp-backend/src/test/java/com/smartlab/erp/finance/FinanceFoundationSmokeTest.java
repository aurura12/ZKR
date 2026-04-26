package com.smartlab.erp.finance;

import com.smartlab.erp.finance.dto.FinanceApiResponse;
import com.smartlab.erp.finance.dto.FinanceResponseMeta;
import com.smartlab.erp.finance.enums.FinanceAdjustmentDirection;
import com.smartlab.erp.finance.support.FinanceAmounts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FinanceFoundationSmokeTest {

    @Test
    void buildsFinanceEnvelopeAndAmounts() {
        FinanceResponseMeta meta = FinanceResponseMeta.builder()
                .page(1)
                .size(20)
                .total(2L)
                .totalPages(1)
                .build();

        FinanceApiResponse<String> response = FinanceApiResponse.success("ok", "payload", meta, "trace-1");

        assertEquals("success", response.getStatus());
        assertEquals("payload", response.getData());
        assertEquals("trace-1", response.getTraceId());
        assertNotNull(response.getMeta());
        assertEquals(new BigDecimal("12.35"), FinanceAmounts.scale(new BigDecimal("12.345")));
        assertEquals(FinanceAdjustmentDirection.DEBIT, FinanceAdjustmentDirection.valueOf("DEBIT"));
    }
}
