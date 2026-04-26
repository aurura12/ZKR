package com.smartlab.erp.finance.controller;

import com.smartlab.erp.config.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import com.smartlab.erp.config.SecurityConfig;
import com.smartlab.erp.finance.dto.FinanceAdjustmentItemView;
import com.smartlab.erp.finance.dto.FinanceAdjustmentCreateResponse;
import com.smartlab.erp.finance.dto.FinanceAdjustmentListResponse;
import com.smartlab.erp.finance.dto.FinanceAuditRef;
import com.smartlab.erp.finance.enums.FinanceAdjustmentDirection;
import com.smartlab.erp.finance.service.FinanceAdjustmentService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceAdjustmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FinanceAdjustmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceAdjustmentService adjustmentService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    void rejectsUnauthenticatedAdjustmentCreate() throws Exception {
        mockMvc.perform(post("/api/adjustment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "U1",
                                  "subject": "manual correction",
                                  "direction": "DEBIT",
                                  "amount": 12.50,
                                  "operator": "auditor"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForSuccessfulAdjustmentCreate() throws Exception {
        when(adjustmentService.create(any(), nullable(String.class))).thenReturn(FinanceAdjustmentCreateResponse.builder().build());

        mockMvc.perform(post("/api/adjustment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "U1",
                                  "subject": "manual correction",
                                  "direction": "DEBIT",
                                  "amount": 12.50,
                                  "operator": "auditor"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Adjustment created"))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForAdjustmentValidationFailure() throws Exception {
        mockMvc.perform(post("/api/adjustment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": " ",
                                  "subject": " ",
                                  "amount": 0
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
    void returnsUnifiedEnvelopeForInvalidAdjustmentDirection() throws Exception {
        mockMvc.perform(post("/api/adjustment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "U1",
                                  "subject": "manual correction",
                                  "direction": "SIDEWAYS",
                                  "amount": 12.50,
                                  "operator": "auditor"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("direction must be DEBIT or CREDIT"))
                .andExpect(jsonPath("$.data").hasJsonPath())
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForAdjustmentListAuditRows() throws Exception {
        when(adjustmentService.list(isNull())).thenReturn(FinanceAdjustmentListResponse.builder()
                .items(java.util.List.of(FinanceAdjustmentItemView.builder()
                        .adjustmentId(9L)
                        .direction(FinanceAdjustmentDirection.DEBIT)
                        .audit(FinanceAuditRef.builder()
                                .sourceTable("MANUAL_RECON")
                                .sourceId(88L)
                                .build())
                        .build()))
                .totalCount(1)
                .build());

        mockMvc.perform(get("/api/adjustment/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Adjustment logs loaded"))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].adjustmentId").value(9))
                .andExpect(jsonPath("$.data.rows[0].audit.sourceTable").value("MANUAL_RECON"))
                .andExpect(jsonPath("$.data.rows[0].audit.sourceId").value(88))
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
