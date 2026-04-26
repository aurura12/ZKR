package com.smartlab.erp.finance.controller;

import com.smartlab.erp.config.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import com.smartlab.erp.config.SecurityConfig;
import com.smartlab.erp.finance.dto.FinanceClearingExecuteResponse;
import com.smartlab.erp.finance.dto.FinanceClearingVentureView;
import com.smartlab.erp.finance.dto.FinanceCostBatchRunResponse;
import com.smartlab.erp.finance.dto.FinanceCostPreviewItem;
import com.smartlab.erp.finance.dto.FinanceCostPreviewResponse;
import com.smartlab.erp.finance.dto.FinanceVentureRef;
import com.smartlab.erp.finance.enums.FinanceBatchStatus;
import com.smartlab.erp.finance.enums.FinanceClearingStatus;
import com.smartlab.erp.finance.service.FinanceClearingService;
import com.smartlab.erp.finance.service.FinanceCostBatchService;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceWorkbenchController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FinanceWorkbenchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceCostBatchService financeCostBatchService;

    @MockBean
    private FinanceClearingService financeClearingService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    void rejectsUnauthenticatedCostBatchRun() throws Exception {
        mockMvc.perform(post("/api/batch/run_cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ledgerMonth": "2026-03"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForSuccessfulCostBatchRun() throws Exception {
        when(financeCostBatchService.runBatch(anyString())).thenReturn(FinanceCostBatchRunResponse.builder()
                .batchId(10L)
                .ledgerMonth("2026-03")
                .status(FinanceBatchStatus.COMPLETED)
                .ventureCount(1)
                .generatedRecordCount(2)
                .totalSettlementCost(new BigDecimal("2000.00"))
                .reusedExistingBatch(false)
                .build());

        mockMvc.perform(post("/api/batch/run_cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ledger_month": "2026-03"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("cost batch completed"))
                .andExpect(jsonPath("$.data.batch_id").value(10))
                .andExpect(jsonPath("$.data.ledger_month").value("2026-03"))
                .andExpect(jsonPath("$.data.generated_record_count").value(2))
                .andExpect(jsonPath("$.data.reused_existing_batch").value(false))
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void acceptsCamelCaseRerunAliasForCostBatchRun() throws Exception {
        when(financeCostBatchService.runBatch(anyString(), anyBoolean())).thenReturn(FinanceCostBatchRunResponse.builder().build());

        mockMvc.perform(post("/api/batch/run_cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ledger_month": "2026-03",
                                  "rerunExistingMonth": true
                                }
                                """))
                .andExpect(status().isOk());

        verify(financeCostBatchService).runBatch("2026-03", true);
    }

    @Test
    @WithMockUser(username = "auditor")
    void acceptsSnakeCaseRerunAliasForCostBatchRun() throws Exception {
        when(financeCostBatchService.runBatch(anyString(), anyBoolean())).thenReturn(FinanceCostBatchRunResponse.builder().build());

        mockMvc.perform(post("/api/batch/run_cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ledger_month": "2026-03",
                                  "rerun_existing_month": true
                                }
                                """))
                .andExpect(status().isOk());

        verify(financeCostBatchService).runBatch("2026-03", true);
    }

    @Test
    @WithMockUser(username = "auditor")
    void acceptsLegacyReplaceAliasForCostBatchRun() throws Exception {
        when(financeCostBatchService.runBatch(anyString(), anyBoolean())).thenReturn(FinanceCostBatchRunResponse.builder().build());

        mockMvc.perform(post("/api/batch/run_cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ledger_month": "2026-03",
                                  "replace_existing_month": true
                                }
                                """))
                .andExpect(status().isOk());

        verify(financeCostBatchService).runBatch("2026-03", true);
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForWorkbenchValidationFailure() throws Exception {
        mockMvc.perform(post("/api/batch/run_cost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ledgerMonth": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").hasJsonPath())
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForPreviewSuccess() throws Exception {
        when(financeCostBatchService.preview(anyLong(), anyString())).thenReturn(FinanceCostPreviewResponse.builder()
                .venture(FinanceVentureRef.builder().legacyVentureId(201L).displayName("V-201").build())
                .ledgerMonth("2026-03")
                .batchId(10L)
                .batchStatus(FinanceBatchStatus.COMPLETED)
                .entryCount(1)
                .totalSettlementCost(new BigDecimal("1000.00"))
                .items(List.of(FinanceCostPreviewItem.builder()
                        .userId("U-2")
                        .userName("Bob")
                        .workHours(new BigDecimal("160.00"))
                        .laborCost(new BigDecimal("500.00"))
                        .finalSettlementCost(new BigDecimal("1000.00"))
                        .build()))
                .build());

        mockMvc.perform(get("/api/batch/preview/1").param("ledgerMonth", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("cost preview loaded"))
                .andExpect(jsonPath("$.data.ledger_month").value("2026-03"))
                .andExpect(jsonPath("$.data.batch_id").value(10))
                .andExpect(jsonPath("$.data.batch_status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.entry_count").value(1))
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForMissingPreviewLedgerMonth() throws Exception {
        mockMvc.perform(get("/api/batch/preview/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").hasJsonPath())
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForClearingVentureList() throws Exception {
        when(financeClearingService.listVentures()).thenReturn(List.of(FinanceClearingVentureView.builder()
                .venture(FinanceVentureRef.builder().legacyVentureId(201L).displayName("V-201").build())
                .ledgerMonth("2026-03")
                .totalCost(new BigDecimal("1200.00"))
                .status(FinanceClearingStatus.PENDING)
                .costReady(true)
                .build()));

        mockMvc.perform(get("/api/clearing/ventures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("clearing ventures loaded"))
                .andExpect(jsonPath("$.data[0].venture.legacyVentureId").value(201))
                .andExpect(jsonPath("$.data[0].costReady").value(true))
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForClearingExecuteWithSnakeCasePayload() throws Exception {
        when(financeClearingService.execute(org.mockito.ArgumentMatchers.any())).thenReturn(FinanceClearingExecuteResponse.builder()
                .clearingSheetId(77L)
                .ledgerMonth("2026-03")
                .finalRevenue(new BigDecimal("1000.00"))
                .totalCost(new BigDecimal("1200.00"))
                .middlewareFee(new BigDecimal("10.00"))
                .netProfit(new BigDecimal("0.00"))
                .lossTransferredToCompany(new BigDecimal("210.00"))
                .status(FinanceClearingStatus.CLEARED)
                .build());

        mockMvc.perform(post("/api/clearing/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "venture_id": 201,
                                  "final_revenue": 1000.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("clearing completed"))
                .andExpect(jsonPath("$.data.clearing_sheet_id").value(77))
                .andExpect(jsonPath("$.data.loss_transferred_to_company").value(210.00))
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void rejectsOutOfScopeClearingExecuteFieldsWithUnifiedEnvelope() throws Exception {
        mockMvc.perform(post("/api/clearing/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "venture_id": 201,
                                  "final_revenue": 1000.00,
                                  "middleware_ids": [99]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(envelopeMetadata());
    }

    private ResultMatcher envelopeMetadata() {
        return result -> {
            String body = result.getResponse().getContentAsString();
            String traceId = JsonPath.read(body, "$.traceId");
            String metaTraceId = JsonPath.read(body, "$.meta.traceId");
            String timestamp = JsonPath.read(body, "$.timestamp");
            String metaTimestamp = JsonPath.read(body, "$.meta.timestamp");

            assertNotNull(traceId);
            assertNotNull(metaTraceId);
            assertNotNull(timestamp);
            assertNotNull(metaTimestamp);
            assertEquals(traceId, metaTraceId);
            assertEquals(timestamp, metaTimestamp);
        };
    }
}
