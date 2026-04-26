package com.smartlab.erp.finance.controller;

import com.smartlab.erp.config.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import com.smartlab.erp.config.SecurityConfig;
import com.smartlab.erp.finance.dto.FinanceAiChatResponse;
import com.smartlab.erp.finance.service.FinanceAiService;
import com.smartlab.erp.finance.service.FinanceRagService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceAiController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FinanceAiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceAiService financeAiService;

    @MockBean
    private FinanceRagService financeRagService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    void rejectsUnauthenticatedFinanceAiChat() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "show me profit"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForSuccessfulFinanceAiChat() throws Exception {
        when(financeAiService.chat(any())).thenReturn(FinanceAiChatResponse.builder()
                .answer("ok")
                .provider("AI")
                .attemptedProvider("AI")
                .fallbackUsed(false)
                .readOnly(true)
                .approvedSourceTypes(java.util.List.of("finance_statements", "finance_wallets"))
                .build());

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "show me profit"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("finance ai chat completed"))
                .andExpect(jsonPath("$.data.provider").value("AI"))
                .andExpect(jsonPath("$.data.attemptedProvider").value("AI"))
                .andExpect(jsonPath("$.data.fallbackUsed").value(false))
                .andExpect(jsonPath("$.data.readOnly").value(true))
                .andExpect(jsonPath("$.data.approvedSourceTypes[0]").value("finance_statements"))
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsReadOnlyFallbackMetadataForFinanceAiChat() throws Exception {
        when(financeAiService.chat(any())).thenReturn(FinanceAiChatResponse.builder()
                .answer("fallback answer")
                .provider("RAG")
                .attemptedProvider("AI")
                .errorMessage("ai unavailable")
                .fallbackUsed(true)
                .fallbackProvider("RAG")
                .fallbackReason("ai unavailable")
                .readOnly(true)
                .streaming(false)
                .build());

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "show me profit"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.provider").value("RAG"))
                .andExpect(jsonPath("$.data.fallbackUsed").value(true))
                .andExpect(jsonPath("$.data.fallbackProvider").value("RAG"))
                .andExpect(jsonPath("$.data.fallbackReason").value("ai unavailable"))
                .andExpect(jsonPath("$.data.readOnly").value(true))
                .andExpect(envelopeMetadata());
    }

    @Test
    @WithMockUser(username = "auditor")
    void returnsUnifiedEnvelopeForFinanceAiValidationFailure() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": " "
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
