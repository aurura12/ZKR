package com.smartlab.erp.controller;

import com.smartlab.erp.dto.WorkflowMemberRoleDTO;
import com.smartlab.erp.finance.service.FinanceExpenseSubmissionService;
import com.smartlab.erp.service.ProductFlowService;
import com.smartlab.erp.service.ProjectService;
import com.smartlab.erp.service.ProjectFinancialMetricsService;
import com.smartlab.erp.service.ResearchFlowService;
import com.smartlab.erp.service.WorkflowMemberRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProjectController.class, ProductFlowController.class, ResearchFlowController.class})
@AutoConfigureMockMvc(addFilters = false)
class WorkflowMemberRoleEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectService projectService;
    @MockBean
    private ProjectFinancialMetricsService projectFinancialMetricsService;
    @MockBean
    private FinanceExpenseSubmissionService financeExpenseSubmissionService;
    @MockBean
    private ProductFlowService productFlowService;
    @MockBean
    private ResearchFlowService researchFlowService;
    @MockBean
    private WorkflowMemberRoleService workflowMemberRoleService;

    @Test
    void projectWorkflowMemberRolesEndpointReturnsExpandedRows() throws Exception {
        when(workflowMemberRoleService.getWorkflowMemberRoles("PROJECT", "p-1")).thenReturn(List.of(
                WorkflowMemberRoleDTO.builder().workflowType("PROJECT").workflowId("p-1").userId("u-1").role("DEV").name("Alice").username("alice").build()
        ));

        mockMvc.perform(get("/api/projects/p-1/workflow-member-roles").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("u-1"))
                .andExpect(jsonPath("$[0].role").value("DEV"));
    }

    @Test
    void productWorkflowMemberRolesEndpointReturnsExpandedRows() throws Exception {
        when(workflowMemberRoleService.getWorkflowMemberRoles("PRODUCT", "p-2")).thenReturn(List.of());

        mockMvc.perform(get("/api/products/p-2/workflow-member-roles").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void researchWorkflowMemberRolesEndpointReturnsExpandedRows() throws Exception {
        when(workflowMemberRoleService.getWorkflowMemberRoles("RESEARCH", "r-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/research/r-1/workflow-member-roles").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
