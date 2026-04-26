package com.smartlab.erp.finance.controller;

import com.smartlab.erp.config.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import com.smartlab.erp.config.SecurityConfig;
import com.smartlab.erp.finance.dto.FinanceMutationResult;
import com.smartlab.erp.finance.service.FinanceBankBalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceReportingController.class)
@Import({SecurityConfig.class, com.smartlab.erp.security.JwtAuthenticationFilter.class})
class FinanceReportingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceBankBalanceService financeBankBalanceService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private com.smartlab.erp.repository.UserRepository userRepository;

    @Test
    void declaresAuthenticatedGuard() throws Exception {
        Method method = FinanceReportingController.class
                .getMethod("recordBankBalance", com.smartlab.erp.finance.dto.FinanceBankBalanceRequest.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("isAuthenticated()", preAuthorize.value());
    }

    @Test
    void rejectsUnauthenticatedAccess() throws Exception {
        mockMvc.perform(post("/api/finance/bank_balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "balance": 123.45,
                                  "operator": "auditor"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "auditor")
    void recordsBankBalanceSnapshot() throws Exception {
        when(financeBankBalanceService.recordBankBalance(any()))
                .thenReturn(FinanceMutationResult.builder()
                        .id("88")
                        .message("bank balance snapshot recorded")
                        .build());

        mockMvc.perform(post("/api/finance/bank_balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "balance": 123.45,
                                  "operator": "auditor",
                                  "remark": "manual close"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("bank balance snapshot recorded"))
                .andExpect(jsonPath("$.data.id").value("88"))
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void rejectsInvalidBankBalanceRequest() throws Exception {
        mockMvc.perform(post("/api/finance/bank_balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "balance": -1,
                                  "operator": "   "
                                 }
                                 """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").hasJsonPath())
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
