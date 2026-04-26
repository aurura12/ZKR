package com.smartlab.erp.security;

import com.smartlab.erp.config.JwtUtil;
import com.smartlab.erp.config.SecurityConfig;
import com.smartlab.erp.controller.ProjectController;
import com.smartlab.erp.controller.UserController;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.finance.controller.FinanceAdjustmentController;
import com.smartlab.erp.finance.controller.FinanceAiController;
import com.smartlab.erp.finance.controller.FinanceDividendController;
import com.smartlab.erp.finance.controller.FinanceReportingController;
import com.smartlab.erp.finance.controller.FinanceWorkbenchController;
import com.smartlab.erp.finance.service.FinanceAdjustmentService;
import com.smartlab.erp.finance.service.FinanceAiService;
import com.smartlab.erp.finance.service.FinanceBankBalanceService;
import com.smartlab.erp.finance.service.FinanceClearingService;
import com.smartlab.erp.finance.service.FinanceCostBatchService;
import com.smartlab.erp.finance.service.FinanceDividendService;
import com.smartlab.erp.finance.service.FinanceRagService;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.service.ProjectService;
import com.smartlab.erp.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        ProjectController.class,
        UserController.class,
        FinanceReportingController.class,
        FinanceAdjustmentController.class,
        FinanceDividendController.class,
        FinanceWorkbenchController.class,
        FinanceAiController.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=12345678901234567890123456789012",
        "jwt.expiration=3600000"
})
class DomainAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private UserService userService;

    @MockBean
    private FinanceBankBalanceService financeBankBalanceService;

    @MockBean
    private FinanceAdjustmentService financeAdjustmentService;

    @MockBean
    private FinanceDividendService financeDividendService;

    @MockBean
    private FinanceCostBatchService financeCostBatchService;

    @MockBean
    private FinanceClearingService financeClearingService;

    @MockBean
    private FinanceAiService financeAiService;

    @MockBean
    private FinanceRagService financeRagService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void financeDomainTokenCannotReachErpRoutes() throws Exception {
        String financeToken = bearerToken(AccountDomain.FINANCE);

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", financeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                        .header("Authorization", financeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void erpDomainTokenCannotReachFinanceRoutes() throws Exception {
        String erpToken = bearerToken(AccountDomain.ERP);

        mockMvc.perform(post("/api/finance/bank_balance")
                        .header("Authorization", erpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/adjustment/list")
                        .header("Authorization", erpToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/batch/run_cost")
                        .header("Authorization", erpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/clearing/ventures")
                        .header("Authorization", erpToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/dividend/list")
                        .header("Authorization", erpToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", erpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/rag/push")
                        .header("Authorization", erpToken))
                .andExpect(status().isForbidden());
    }

    private String bearerToken(AccountDomain accountDomain) {
        return "Bearer " + jwtUtil.generateToken(
                "U-100",
                accountDomain.name().toLowerCase() + "-user",
                "Domain User",
                "RESEARCH",
                accountDomain.name().toLowerCase() + "@example.com",
                accountDomain
        );
    }
}
