package com.smartlab.erp.finance.controller;

import com.smartlab.erp.config.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import com.smartlab.erp.config.SecurityConfig;
import com.smartlab.erp.finance.dto.FinanceDividendConfirmResponse;
import com.smartlab.erp.finance.dto.FinanceDividendListResponse;
import com.smartlab.erp.finance.dto.FinanceDividendPrepareResponse;
import com.smartlab.erp.finance.dto.FinanceMutationResult;
import com.smartlab.erp.finance.service.FinanceDividendService;
import com.smartlab.erp.finance.enums.FinanceDividendStatus;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceDividendController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FinanceDividendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceDividendService dividendService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    void rejectsUnauthenticatedDividendPrepare() throws Exception {
        mockMvc.perform(post("/api/dividend/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "P1"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForSuccessfulDividendPrepare() throws Exception {
        when(dividendService.prepare(any())).thenReturn(FinanceDividendPrepareResponse.builder().build());

        mockMvc.perform(post("/api/dividend/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "P1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dividend sheets prepared"))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForDividendValidationFailure() throws Exception {
        mockMvc.perform(post("/api/dividend/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": " "
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
    void returnsUnifiedEnvelopeForDividendListSuccess() throws Exception {
        when(dividendService.list(isNull(), isNull())).thenReturn(FinanceDividendListResponse.builder().build());

        mockMvc.perform(get("/api/dividend/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dividend sheets loaded"))
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void forwardsDividendStatusFilterAndReturnsStableListShape() throws Exception {
        when(dividendService.list("P1", FinanceDividendStatus.PENDING)).thenReturn(FinanceDividendListResponse.builder()
                .items(java.util.List.of())
                .totalCount(1)
                .build());

        mockMvc.perform(get("/api/dividend/list")
                        .param("projectId", "P1")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.rows").isArray())
                .andExpect(envelopeMetadata());

        verify(dividendService).list("P1", FinanceDividendStatus.PENDING);
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForInvalidDividendStatusQuery() throws Exception {
        mockMvc.perform(get("/api/dividend/list").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").hasJsonPath())
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForDividendConfirmWithStableResultShape() throws Exception {
        when(dividendService.confirm(any(), any())).thenReturn(FinanceDividendConfirmResponse.builder()
                .confirmedCount(1)
                .bankBalanceBefore(new java.math.BigDecimal("100.00"))
                .bankBalanceAfter(new java.math.BigDecimal("70.00"))
                .walletResults(java.util.List.of(FinanceMutationResult.builder()
                        .id("21")
                        .message("Wallet posted")
                        .build()))
                .build());

        mockMvc.perform(post("/api/dividend/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "P1",
                                  "operator": "auditor"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dividend sheets confirmed"))
                .andExpect(jsonPath("$.data.confirmedCount").value(1))
                .andExpect(jsonPath("$.data.bankBalanceBefore").value(100.00))
                .andExpect(jsonPath("$.data.bankBalanceAfter").value(70.00))
                .andExpect(jsonPath("$.data.results[0].id").value("21"))
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
