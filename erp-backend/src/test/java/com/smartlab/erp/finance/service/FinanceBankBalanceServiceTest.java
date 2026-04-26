package com.smartlab.erp.finance.service;

import com.smartlab.erp.finance.dto.FinanceBankBalanceRequest;
import com.smartlab.erp.finance.entity.FinanceBankBalanceSnapshot;
import com.smartlab.erp.finance.repository.FinanceBankBalanceSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceBankBalanceServiceTest {

    @Mock
    private FinanceBankBalanceSnapshotRepository bankBalanceSnapshotRepository;

    @InjectMocks
    private FinanceBankBalanceService financeBankBalanceService;

    @Test
    void recordsBankBalanceSnapshotAndReturnsMutationResult() {
        when(bankBalanceSnapshotRepository.save(any(FinanceBankBalanceSnapshot.class))).thenAnswer(invocation -> {
            FinanceBankBalanceSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(55L);
            return snapshot;
        });

        FinanceBankBalanceRequest request = FinanceBankBalanceRequest.builder()
                .balance(new BigDecimal("123.456"))
                .operator("  auditor ")
                .remark("manual close")
                .build();

        var result = financeBankBalanceService.recordBankBalance(request);

        assertEquals("55", result.getId());
        assertEquals("bank balance snapshot recorded", result.getMessage());

        ArgumentCaptor<FinanceBankBalanceSnapshot> captor = ArgumentCaptor.forClass(FinanceBankBalanceSnapshot.class);
        verify(bankBalanceSnapshotRepository).save(captor.capture());
        assertEquals(new BigDecimal("123.46"), captor.getValue().getBalance());
        assertEquals("auditor", captor.getValue().getOperator());
        assertEquals("manual close", captor.getValue().getRemark());
        assertNotNull(captor.getValue().getSnapshotAt());
    }
}
