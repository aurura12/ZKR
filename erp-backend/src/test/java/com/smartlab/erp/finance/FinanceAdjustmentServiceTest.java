package com.smartlab.erp.finance;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.finance.dto.FinanceAdjustmentCreateRequest;
import com.smartlab.erp.finance.dto.FinanceAdjustmentListResponse;
import com.smartlab.erp.finance.entity.FinanceAdjustmentLog;
import com.smartlab.erp.finance.entity.FinanceWalletAccount;
import com.smartlab.erp.finance.entity.FinanceWalletTransaction;
import com.smartlab.erp.finance.enums.FinanceAdjustmentDirection;
import com.smartlab.erp.finance.repository.FinanceAdjustmentLogRepository;
import com.smartlab.erp.finance.repository.FinanceWalletAccountRepository;
import com.smartlab.erp.finance.repository.FinanceWalletTransactionRepository;
import com.smartlab.erp.finance.service.FinanceAdjustmentService;
import com.smartlab.erp.finance.service.FinanceReferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceAdjustmentServiceTest {

    @Mock
    private FinanceReferenceService referenceService;
    @Mock
    private FinanceAdjustmentLogRepository adjustmentLogRepository;
    @Mock
    private FinanceWalletAccountRepository walletAccountRepository;
    @Mock
    private FinanceWalletTransactionRepository walletTransactionRepository;

    @InjectMocks
    private FinanceAdjustmentService adjustmentService;

    @Test
    void createAppliesDebitIntoWalletAndAuditLog() {
        User user = user("U-1", "alice");
        FinanceWalletAccount wallet = FinanceWalletAccount.builder()
                .id(1L)
                .owner(user)
                .balance(new BigDecimal("50.00"))
                .totalDividendEarned(BigDecimal.ZERO)
                .totalRoyaltyEarned(BigDecimal.ZERO)
                .totalAdjustmentAmount(BigDecimal.ZERO)
                .build();

        when(referenceService.getRequiredUser("U-1")).thenReturn(user);
        when(referenceService.getOrCreateWallet("U-1")).thenReturn(wallet);
        when(walletAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(adjustmentLogRepository.save(any())).thenAnswer(invocation -> {
            FinanceAdjustmentLog log = invocation.getArgument(0);
            log.setId(99L);
            return log;
        });

        adjustmentService.create(FinanceAdjustmentCreateRequest.builder()
                .userId("U-1")
                .subject("MANUAL_RECON")
                .direction(FinanceAdjustmentDirection.DEBIT)
                .amount(new BigDecimal("20.00"))
                .remark("fix")
                .refDocNo("DOC-9")
                .build(), "ops-user");

        assertEquals(new BigDecimal("70.00"), wallet.getBalance());
        assertEquals(new BigDecimal("20.00"), wallet.getTotalAdjustmentAmount());
        verify(walletTransactionRepository).save(any(FinanceWalletTransaction.class));
    }

    @Test
    void listExposesRemarkAndReferenceDocument() {
        User user = user("U-1", "alice");
        when(adjustmentLogRepository.findTop100ByOrderByIdDesc()).thenReturn(List.of(
                FinanceAdjustmentLog.builder()
                        .id(1L)
                        .user(user)
                        .direction(FinanceAdjustmentDirection.DEBIT)
                        .amount(new BigDecimal("30.00"))
                        .reason("manual note")
                        .refDocNo("REF-1")
                        .sourceTable("MANUAL_RECON")
                        .createdBy("ops")
                        .build()));

        FinanceAdjustmentListResponse response = adjustmentService.list(null);

        assertEquals("manual note", response.getItems().get(0).getRemark());
        assertEquals("REF-1", response.getItems().get(0).getRefDocNo());
    }

    @Test
    void listExposesAuditRowsForFinanceEnvelopeConsumers() {
        User user = user("U-1", "alice");
        when(adjustmentLogRepository.findTop100ByOrderByIdDesc()).thenReturn(List.of(
                FinanceAdjustmentLog.builder()
                        .id(1L)
                        .user(user)
                        .direction(FinanceAdjustmentDirection.DEBIT)
                        .amount(new BigDecimal("30.00"))
                        .reason("manual note")
                        .sourceTable("MANUAL_RECON")
                        .sourceId(88L)
                        .createdBy("ops")
                        .build()));

        FinanceAdjustmentListResponse response = adjustmentService.list(null);

        assertEquals(1, response.getRows().size());
        assertEquals("MANUAL_RECON", response.getRows().get(0).getAudit().getSourceTable());
        assertEquals(88L, response.getRows().get(0).getAudit().getSourceId());
    }

    @Test
    void createRejectsInvalidAmount() {
        assertThrows(IllegalArgumentException.class, () -> adjustmentService.create(
                FinanceAdjustmentCreateRequest.builder()
                        .userId("U-1")
                        .subject("MANUAL_RECON")
                        .direction(FinanceAdjustmentDirection.DEBIT)
                        .amount(BigDecimal.ZERO)
                        .build(),
                "ops-user"));
    }

    @Test
    void listCalculatesNetAdjustment() {
        User user = user("U-1", "alice");
        when(adjustmentLogRepository.findTop100ByOrderByIdDesc()).thenReturn(List.of(
                FinanceAdjustmentLog.builder()
                        .id(1L)
                        .user(user)
                        .direction(FinanceAdjustmentDirection.DEBIT)
                        .amount(new BigDecimal("30.00"))
                        .sourceTable("MANUAL_RECON")
                        .createdBy("ops")
                        .build(),
                FinanceAdjustmentLog.builder()
                        .id(2L)
                        .user(user)
                        .direction(FinanceAdjustmentDirection.CREDIT)
                        .amount(new BigDecimal("10.00"))
                        .sourceTable("MANUAL_RECON")
                        .createdBy("ops")
                        .build()));

        FinanceAdjustmentListResponse response = adjustmentService.list(null);

        assertEquals(2, response.getTotalCount());
        assertEquals(new BigDecimal("30.00"), response.getDebitTotal());
        assertEquals(new BigDecimal("10.00"), response.getCreditTotal());
        assertEquals(new BigDecimal("20.00"), response.getNetAdjustment());
    }

    private User user(String userId, String username) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setName(username);
        user.setRole("RESEARCH");
        return user;
    }
}
