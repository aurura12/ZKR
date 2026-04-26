package com.smartlab.erp.finance;

import com.smartlab.erp.finance.dto.FinanceAiChatRequest;
import com.smartlab.erp.finance.dto.FinanceAiChatResponse;
import com.smartlab.erp.finance.dto.FinanceAiContextBlock;
import com.smartlab.erp.finance.dto.FinanceRagPushResponse;
import com.smartlab.erp.finance.dto.FinanceRagQueryRequest;
import com.smartlab.erp.finance.dto.FinanceRagQueryResponse;
import com.smartlab.erp.finance.dto.FinanceDividendListResponse;
import com.smartlab.erp.finance.dto.FinanceAdjustmentListResponse;
import com.smartlab.erp.finance.dto.FinanceStatementsResponse;
import com.smartlab.erp.finance.dto.FinanceTransactionListResponse;
import com.smartlab.erp.finance.dto.FinanceWalletOverviewResponse;
import com.smartlab.erp.finance.entity.FinanceKnowledgeDocument;
import com.smartlab.erp.finance.repository.FinanceKnowledgeDocumentRepository;
import com.smartlab.erp.finance.service.FinanceExternalRagClient;
import com.smartlab.erp.finance.service.FinanceAdjustmentService;
import com.smartlab.erp.finance.service.FinanceAiContextService;
import com.smartlab.erp.finance.service.FinanceAiGateway;
import com.smartlab.erp.finance.service.FinanceAiService;
import com.smartlab.erp.finance.service.FinanceClearingService;
import com.smartlab.erp.finance.service.FinanceDividendService;
import com.smartlab.erp.finance.service.FinanceRagService;
import com.smartlab.erp.finance.service.FinanceReportingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceAiServiceTest {

    @Mock
    private FinanceReportingService financeReportingService;
    @Mock
    private FinanceClearingService financeClearingService;
    @Mock
    private FinanceDividendService financeDividendService;
    @Mock
    private FinanceAdjustmentService financeAdjustmentService;
    @Mock
    private FinanceKnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock
    private FinanceAiGateway financeAiGateway;
    @Mock
    private FinanceExternalRagClient financeExternalRagClient;
    @Mock
    private FinanceAiContextService financeAiContextService;

    private FinanceRagService financeRagService;
    private FinanceAiService financeAiService;

    @BeforeEach
    void setUp() {
        financeRagService = new FinanceRagService(financeAiContextService, knowledgeDocumentRepository, financeExternalRagClient);
        financeAiService = new FinanceAiService(financeAiContextService, financeRagService, financeAiGateway);
    }

    @Test
    void chatReturnsAiMetadataWhenGatewaySucceeds() {
        stubFinanceContext();
        when(financeAiGateway.generateAnswer(eq("What is the latest bank balance?"), any())).thenReturn("AI answer");

        FinanceAiChatResponse response = financeAiService.chat(FinanceAiChatRequest.builder()
                .message("What is the latest bank balance?")
                .clearHistory(Boolean.FALSE)
                .build());

        assertEquals("AI", response.getProvider());
        assertEquals("AI", response.getAttemptedProvider());
        assertFalse(response.isFallbackUsed());
        assertTrue(response.isReadOnly());
        assertTrue(response.getApprovedSourceTypes().contains("finance_statements"));
        assertTrue(response.getContextBlocks().stream().anyMatch(block -> block.getContent().contains("net cash flow 400.00")));
    }

    @Test
    void chatFallsBackToRagWhenGatewayFails() {
        stubFinanceContext();
        when(financeAiGateway.generateAnswer(any(), any())).thenThrow(new IllegalStateException("provider offline"));

        FinanceAiChatResponse response = financeAiService.chat(FinanceAiChatRequest.builder()
                .message("What is the latest bank balance?")
                .clearHistory(Boolean.FALSE)
                .build());

        assertTrue(response.isFallbackUsed());
        assertEquals("RAG", response.getProvider());
        assertEquals("AI", response.getAttemptedProvider());
        assertEquals("RAG", response.getFallbackProvider());
        assertEquals("provider offline", response.getFallbackReason());
        assertEquals("provider offline", response.getErrorMessage());
        assertTrue(response.isReadOnly());
        assertFalse(response.isStreaming());
        assertTrue(response.getDataRows().size() >= 1);
        assertTrue(response.getAnswer().contains("5200.00"));
    }

    @Test
    void chatFallsBackToRagWhenContextAssemblyFails() {
        when(financeReportingService.getStatements()).thenThrow(new IllegalStateException("snapshot unavailable"));
        when(knowledgeDocumentRepository.findByActiveTrueOrderByUpdatedAtDesc()).thenReturn(List.of(
                FinanceKnowledgeDocument.builder()
                        .topic("Statements Snapshot")
                        .sourceTable("finance_statements")
                        .sourceId(202603L)
                        .content("Ledger month 2026-03, net cash flow 400.00, bank balance 5200.00")
                        .active(Boolean.TRUE)
                        .build()
        ));

        FinanceAiChatResponse response = financeAiService.chat(FinanceAiChatRequest.builder()
                .message("What is the latest bank balance?")
                .clearHistory(Boolean.FALSE)
                .build());

        assertTrue(response.isFallbackUsed());
        assertEquals("RAG", response.getProvider());
        assertEquals("AI", response.getAttemptedProvider());
        assertEquals("RAG", response.getFallbackProvider());
        assertEquals("snapshot unavailable", response.getFallbackReason());
        assertEquals("snapshot unavailable", response.getErrorMessage());
        assertTrue(response.isReadOnly());
        assertTrue(response.getAnswer().contains("net cash flow 400.00"));
        assertTrue(response.getApprovedSourceTypes().contains("finance_statements"));
    }

    @Test
    void ragQueryReturnsContextRowsBoundedToFinanceData() {
        stubFinanceContext();
        FinanceRagQueryResponse response = financeRagService.query(FinanceRagQueryRequest.builder()
                .prompt("pending dividends")
                .limit(2)
                .build());

        assertTrue(response.getDataRows().size() >= 1);
        assertTrue(response.getDataRows().stream().anyMatch(row -> "Dividend Summary".equals(row.getTitle())));
        assertTrue(response.getAnswer().contains("pending dividends"));
        assertEquals(6, response.getContextBlocks().size());
        assertTrue(response.getContextBlocks().stream().anyMatch(block -> block.getContent().contains("net cash flow 400.00")));
    }

    @Test
    void ragPushRefreshesKnowledgeDocumentsFromCurrentFinanceContext() {
        stubFinanceContext();
        FinanceKnowledgeDocument existing = FinanceKnowledgeDocument.builder()
                .id(4L)
                .topic("Old Snapshot")
                .active(Boolean.TRUE)
                .updatedAt(Instant.parse("2026-03-11T10:00:00Z"))
                .build();
        when(knowledgeDocumentRepository.findByActiveTrueOrderByUpdatedAtDesc()).thenReturn(List.of(existing));
        when(knowledgeDocumentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FinanceRagPushResponse response = financeRagService.push();

        assertEquals(6, response.getDocumentCount());
        assertEquals("finance-rag-index", response.getIndexName());
        assertEquals("ACTIVE", response.getStatus());
        assertFalse(existing.getActive());
        ArgumentCaptor<List<FinanceKnowledgeDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeDocumentRepository, times(2)).saveAll(captor.capture());
        assertEquals(6, captor.getAllValues().get(1).size());
        assertTrue(captor.getAllValues().get(1).stream().allMatch(FinanceKnowledgeDocument::getActive));
    }

    private void stubFinanceContext() {
        when(financeAiContextService.approvedSourceTypes()).thenReturn(List.of(
                "erp_project_portfolio",
                "erp_project_summary",
                "erp_member_activity",
                "erp_project_chat",
                "erp_internal_message",
                "erp_git_repository",
                "erp_project_member_ranking",
                "erp_user_participation_ranking",
                "erp_project_recent_activity_ranking"
        ));
        when(financeAiContextService.buildContextBlocks()).thenReturn(List.of(
                FinanceAiContextBlock.builder()
                        .title("Project Portfolio Overview")
                        .content("Projects 2, memberships 3, managers 2, project flow 1, product flow 1, research flow 0, statuses [ACTIVE=2]")
                        .sourceType("erp_project_portfolio")
                        .sourceKey("all-projects")
                        .build(),
                FinanceAiContextBlock.builder()
                        .title("Statements Snapshot")
                        .content("Ledger month 2026-03, net cash flow 400.00, bank balance 5200.00")
                        .sourceType("erp_project_summary")
                        .sourceKey("summary-1")
                        .build()
        ));
        when(financeReportingService.getStatements()).thenReturn(FinanceStatementsResponse.builder()
                .latestLedgerMonth("2026-03")
                .incomeStatement(FinanceStatementsResponse.IncomeStatement.builder()
                        .totalRevenue(new BigDecimal("8600.00"))
                        .totalCost(new BigDecimal("5200.00"))
                        .totalProfit(new BigDecimal("3400.00"))
                        .build())
                .cashFlowStatement(FinanceStatementsResponse.CashFlowStatement.builder()
                        .totalIn(new BigDecimal("1800.00"))
                        .totalOut(new BigDecimal("1400.00"))
                        .netCashFlow(new BigDecimal("400.00"))
                        .build())
                .reconciliation(FinanceStatementsResponse.Reconciliation.builder()
                        .actualBankBalance(new BigDecimal("5200.00"))
                        .variance(BigDecimal.ZERO.setScale(2))
                        .snapshotRecorded(true)
                        .snapshotAt(Instant.parse("2026-03-12T08:30:00Z"))
                        .build())
                .build());
        when(financeReportingService.getWalletOverview()).thenReturn(FinanceWalletOverviewResponse.builder()
                .summary(FinanceWalletOverviewResponse.Summary.builder()
                        .walletCount(3)
                        .totalBalance(new BigDecimal("1800.00"))
                        .totalDividendEarned(new BigDecimal("700.00"))
                        .totalRoyaltyEarned(new BigDecimal("120.00"))
                        .totalAdjustmentAmount(new BigDecimal("25.00"))
                        .build())
                .build());
        when(financeReportingService.getTransactions(10, null, null, null, null)).thenReturn(FinanceTransactionListResponse.builder()
                .limit(10)
                .totalCount(4L)
                .items(List.of(FinanceTransactionListResponse.TransactionRow.builder()
                        .id(41L)
                        .transactionType("DIVIDEND")
                        .amount(new BigDecimal("300.00"))
                        .audit(com.smartlab.erp.finance.dto.FinanceAuditRef.builder()
                                .sourceTable("finance_dividend_sheet")
                                .sourceId(7L)
                                .build())
                        .build()))
                .build());
        when(financeClearingService.listVentures()).thenReturn(List.of());
        when(financeDividendService.list(null, null)).thenReturn(FinanceDividendListResponse.builder()
                .totalCount(3)
                .totalAmount(new BigDecimal("300.00"))
                .build());
        when(financeAdjustmentService.list(null)).thenReturn(FinanceAdjustmentListResponse.builder()
                .totalCount(2)
                .debitTotal(new BigDecimal("30.00"))
                .creditTotal(new BigDecimal("5.00"))
                .netAdjustment(new BigDecimal("25.00"))
                .build());
    }
}
