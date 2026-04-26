# Finance Console Rebuild Implementation Plan

> Historical note (2026-03-16): this is a development-phase rebuild plan, not the current operational state of the repo. The repo is now in maintenance/update mode. Use `workflow.md`, `docs/deployment/docker-mother-child.md`, and `docs/superpowers/current-project-memory.md` for current work.

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the finance console in `erp-backend` and `lab-erp-demo` with backend-owned finance rules, phased delivery from P0 to P2, and stable frontend integration against finalized API contracts.

**Architecture:** The plan is backend-first. Each finance capability is stabilized in `erp-backend` through DTO, service, controller, and test coverage before `lab-erp-demo` replaces its current scaffolded pages with contract-driven state and UI. High-risk write paths stay transactional and authoritative on the backend; frontend logic remains display-oriented.

**Tech Stack:** Spring Boot 3.2, Spring MVC, Spring Security, Spring Data JPA, JUnit; Vue 3, Pinia, Vue Router, Element Plus, Axios, Vite

---

## File Structure Map

### Backend existing anchors in `erp-backend`

- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceReportingService.java` - aggregate statements, wallet overview, and transaction list
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceAdjustmentService.java` - adjustment validation, persistence, list semantics
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceClearingService.java` - venture readiness, clearing transaction, loss transfer, royalty handling
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceDividendService.java` - prepare/list/confirm flows with bank-balance assertion
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceCostBatchService.java` - ledger-month run and venture preview
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceAiService.java` - AI-first answer path
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceRagService.java` - query/push behavior and downgrade support
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceAiContextService.java` - finance-scoped context assembly
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceWorkbenchController.java` - cost batch and clearing endpoints
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAdjustmentController.java` - adjustment endpoints and errors
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceDividendController.java` - dividend endpoints and errors
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAiController.java` - AI/RAG endpoints and errors
- Modify: `erp-backend/src/main/java/com/smartlab/erp/config/SecurityConfig.java` - finance-route authorization boundaries
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/*.java` - request/response contract alignment with `ambition.md`

### Backend likely additions in `erp-backend`

- Create: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceBankBalanceService.java`
- Create: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceReportingController.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceReportingServiceTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceBankBalanceServiceTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceAdjustmentServiceTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceClearingServiceTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceDividendServiceTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceCostBatchServiceTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAdjustmentControllerTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceWorkbenchControllerTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceDividendControllerTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAiControllerTest.java`

### Frontend existing anchors in `lab-erp-demo`

- Modify: `lab-erp-demo/src/api/finance/overview.js` - overview API contract alignment
- Modify: `lab-erp-demo/src/api/finance/workbench.js` - workbench API contract alignment
- Modify: `lab-erp-demo/src/api/finance/wallets.js` - wallets and bank snapshot contract alignment
- Modify: `lab-erp-demo/src/stores/financeOverviewStore.js` - overview loading and bank snapshot refresh flow
- Modify: `lab-erp-demo/src/stores/financeWorkbenchStore.js` - cost batch, clearing, dividend, adjustment state
- Modify: `lab-erp-demo/src/stores/financeWalletStore.js` - wallet summary and transactions state
- Modify: `lab-erp-demo/src/stores/financeAiStore.js` - AI chat, fallback messaging, RAG state
- Modify: `lab-erp-demo/src/views/finance/FinanceOverviewView.vue` - KPI, risk table, reconciliation, bank snapshot form
- Modify: `lab-erp-demo/src/views/finance/FinanceWalletsView.vue` - wallet and transaction tables
- Modify: `lab-erp-demo/src/views/finance/AdjustmentCenterView.vue` - adjustment form and audit list
- Modify: `lab-erp-demo/src/views/finance/ClearingCenterView.vue` - venture list and clearing submit flow
- Modify: `lab-erp-demo/src/views/finance/DividendCenterView.vue` - prepare/list/confirm flow
- Modify: `lab-erp-demo/src/views/finance/CostBatchView.vue` - run and preview flow
- Modify: `lab-erp-demo/src/views/finance/FinanceAiChatView.vue` - chat and downgrade UX
- Modify: `lab-erp-demo/src/views/finance/RagSearchView.vue` - retrieval flow
- Modify: `lab-erp-demo/src/utils/financeAdapters.js` - envelope unwrapping and list/mutation normalization
- Modify: `lab-erp-demo/src/utils/financeEnums.js` - finance statuses and labels
- Modify: `lab-erp-demo/src/utils/financeFormatters.js` - currency/date/percentage helpers used by new views

### Frontend likely additions in `lab-erp-demo`

- Create: `lab-erp-demo/src/components/finance/FinanceMetricCard.vue`
- Create: `lab-erp-demo/src/components/finance/FinanceStatusTag.vue`
- Create: `lab-erp-demo/src/components/finance/FinanceConfirmDialog.vue`
- Create: `lab-erp-demo/src/components/finance/FinanceEmptyState.vue`
- Create: `lab-erp-demo/src/components/finance/FinanceErrorState.vue`
- Create: `lab-erp-demo/scripts/finance-adapters.test.mjs`
- Create: `lab-erp-demo/scripts/finance-workbench-store.test.mjs`
- Create: `lab-erp-demo/scripts/finance-overview-links.test.mjs`

## Chunk 1: Backend P0/P1/P2 contract stabilization in `erp-backend`

### Task 1: Lock the reporting and reconciliation contract

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceReportingService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceStatementsResponse.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceReportingServiceTest.java`

- [ ] **Step 1: Write the failing service test for statement aggregates and reconciliation**

```java
@Test
void getStatements_includesKpisRiskRowsAndReconciliationVariance() {
    FinanceStatementsResponse response = service.getStatements();
    assertThat(response.getKpis()).isNotEmpty();
    assertThat(response.getReconciliation().isSnapshotRecorded()).isTrue();
    assertThat(response.getRiskRows()).isNotNull();
}
```

- [ ] **Step 2: Run test to verify it fails for missing or mismatched fields**

Run: `mvn -Dtest=FinanceReportingServiceTest test`
Expected: FAIL with assertion or setup mismatch showing the current response does not fully match the desired contract.

- [ ] **Step 3: Update `FinanceStatementsResponse` to match `ambition.md` fields without adding unused structure**

```java
@Data
@Builder
public class FinanceStatementsResponse {
    private List<KpiCard> kpis;
    private IncomeStatement incomeStatement;
    private BalanceSheet balanceSheet;
    private CashFlowStatement cashFlowStatement;
    private Reconciliation reconciliation;
    private List<RiskRow> riskRows;
}
```

- [ ] **Step 4: Update `FinanceReportingService#getStatements()` to compute KPI cards, statement sections, theoretical balance, variance, and risk rows from authoritative repositories**

```java
BigDecimal theoreticalBalance = FinanceAmounts.add(netCashFlow, adjustmentNet);
BigDecimal variance = FinanceAmounts.subtract(actualBankBalance, theoreticalBalance);
boolean matched = latestSnapshot.isPresent() && variance.compareTo(BigDecimal.ZERO.setScale(2, HALF_UP)) == 0;
```

- [ ] **Step 5: Run the reporting test to verify it passes**

Run: `mvn -Dtest=FinanceReportingServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceReportingService.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceStatementsResponse.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceReportingServiceTest.java
git commit -m "feat: stabilize finance reporting contract"
```

### Task 2: Add bank snapshot write behavior to the reporting surface

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceBankBalanceService.java`
- Create: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceReportingController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceBankBalanceRequest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceBankBalanceServiceTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceReportingControllerTest.java`

- [ ] **Step 1: Write the failing controller test for `POST /api/finance/bank_balance`**

```java
mockMvc.perform(post("/api/finance/bank_balance")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"balance":1200.50,"operator":"finance-admin","remark":"month-end"}
        """))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.success").value(true))
    .andExpect(jsonPath("$.message").value("bank balance snapshot recorded"))
    .andExpect(jsonPath("$.data.id").exists())
    .andExpect(jsonPath("$.traceId").isString());
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run: `mvn -Dtest=FinanceReportingControllerTest test`
Expected: FAIL with missing route or response mismatch.

- [ ] **Step 3: Write the failing service test for bank snapshot persistence and returned mutation id**

```java
@Test
void recordBankBalance_persistsSnapshotAndReturnsMutationId() {
    FinanceMutationResult result = service.recordBankBalance(request);
    assertThat(result.getId()).isNotBlank();
    assertThat(result.getMessage()).isEqualTo("bank balance snapshot recorded");
}
```

- [ ] **Step 4: Run the bank-balance service test to verify it fails**

Run: `mvn -Dtest=FinanceBankBalanceServiceTest test`
Expected: FAIL until the dedicated service exists.

- [ ] **Step 5: Keep request validation minimal and explicit in `FinanceBankBalanceRequest`**

```java
@NotNull
@DecimalMin("0.00")
private BigDecimal balance;

@NotBlank
private String operator;
```

- [ ] **Step 6: Move bank snapshot write logic into a focused `FinanceBankBalanceService` and expose it through `FinanceReportingController`**

```java
@PostMapping("/api/finance/bank_balance")
public ResponseEntity<FinanceApiResponse<?>> recordBankBalance(@Valid @RequestBody FinanceBankBalanceRequest request) {
    return ResponseEntity.ok(FinanceApiResponse.success(
        "bank balance snapshot recorded",
        financeBankBalanceService.recordBankBalance(request),
        null,
        traceId));
}
```

- [ ] **Step 7: Run the service and controller tests to verify they pass**

Run: `mvn -Dtest=FinanceBankBalanceServiceTest,FinanceReportingControllerTest test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceBankBalanceService.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceReportingController.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceBankBalanceRequest.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceBankBalanceServiceTest.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceReportingControllerTest.java
git commit -m "feat: add bank balance snapshot endpoint"
```

### Task 2A: Enforce finance authorization boundaries and unified response envelope

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/config/SecurityConfig.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceReportingController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAdjustmentController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceDividendController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceWorkbenchController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAiController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceApiResponse.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceReportingControllerTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAdjustmentControllerTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceDividendControllerTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceWorkbenchControllerTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAiControllerTest.java`

- [ ] **Step 1: Write failing controller tests for forbidden high-risk writes and consistent envelope fields**

```java
mockMvc.perform(post("/api/dividend/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
    .andExpect(status().isForbidden());

mockMvc.perform(post("/api/finance/bank_balance").contentType(MediaType.APPLICATION_JSON).content(body))
    .andExpect(status().isForbidden());

mockMvc.perform(get("/api/adjustment/list"))
    .andExpect(jsonPath("$.success").exists())
    .andExpect(jsonPath("$.message").exists())
    .andExpect(jsonPath("$.data").exists())
    .andExpect(jsonPath("$.traceId").exists());
```

- [ ] **Step 2: Run the controller tests to verify they fail**

Run: `mvn -Dtest=FinanceReportingControllerTest,FinanceAdjustmentControllerTest,FinanceDividendControllerTest,FinanceWorkbenchControllerTest,FinanceAiControllerTest test`
Expected: FAIL until authorization and envelope behavior are consistent.

- [ ] **Step 3: Tighten finance write/read permissions in `SecurityConfig.java` and endpoint-level annotations to match the approved role matrix**

- [ ] **Step 4: Make each finance controller return the same envelope keys on success and validation failure**

- [ ] **Step 5: Run the controller tests to verify they pass**

Run: `mvn -Dtest=FinanceReportingControllerTest,FinanceAdjustmentControllerTest,FinanceDividendControllerTest,FinanceWorkbenchControllerTest,FinanceAiControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/config/SecurityConfig.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceReportingController.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAdjustmentController.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceDividendController.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceWorkbenchController.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAiController.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceApiResponse.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceReportingControllerTest.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAdjustmentControllerTest.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceDividendControllerTest.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceWorkbenchControllerTest.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAiControllerTest.java
git commit -m "feat: enforce finance auth and response contracts"
```

### Task 3: Stabilize adjustment create/list behavior and validation

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceAdjustmentService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAdjustmentController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceAdjustmentCreateRequest.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceAdjustmentListResponse.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceAdjustmentServiceTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAdjustmentControllerTest.java`

- [ ] **Step 1: Write the failing service test for valid DEBIT/CREDIT validation and positive amount requirement**

```java
@Test
void create_rejectsNonPositiveAmount() {
    assertThatThrownBy(() -> service.create(request, "user-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("amount");
}
```

- [ ] **Step 2: Run the service test to verify it fails**

Run: `mvn -Dtest=FinanceAdjustmentServiceTest test`
Expected: FAIL until the validation path is implemented or aligned.

- [ ] **Step 3: Implement minimal validation and audit-preserving create/list mapping in service and DTOs**

```java
if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
    throw new IllegalArgumentException("adjustment amount must be greater than zero");
}
```

- [ ] **Step 4: Add controller test coverage for create and list envelope shape**

```java
mockMvc.perform(get("/api/adjustment/list"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.items").isArray());
```

- [ ] **Step 5: Run service and controller tests to verify they pass**

Run: `mvn -Dtest=FinanceAdjustmentServiceTest,FinanceAdjustmentControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceAdjustmentService.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAdjustmentController.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceAdjustmentCreateRequest.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceAdjustmentListResponse.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceAdjustmentServiceTest.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAdjustmentControllerTest.java
git commit -m "feat: stabilize finance adjustment flows"
```

### Task 4: Make clearing venture listing and execution authoritative

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceClearingService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceWorkbenchController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceClearingExecuteRequest.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceClearingExecuteResponse.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceClearingServiceTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceWorkbenchControllerTest.java`

- [ ] **Step 1: Write the failing service tests for positive-profit and loss-transfer clearing paths**

```java
@Test
void execute_transfersLossToCompanyWhenProfitIsNegative() {
    FinanceClearingExecuteResponse response = service.execute(request);
    assertThat(response.getNetProfit()).isEqualByComparingTo("0.00");
    assertThat(response.getLossTransferredToCompany()).isGreaterThan(BigDecimal.ZERO);
}
```

- [ ] **Step 2: Run the clearing tests to verify they fail**

Run: `mvn -Dtest=FinanceClearingServiceTest test`
Expected: FAIL on formula or transaction assertions.

- [ ] **Step 3: Implement venture readiness list and clearing transaction with idempotency guard**

```java
BigDecimal royaltyFee = finalRevenue.multiply(new BigDecimal("0.01")).multiply(BigDecimal.valueOf(callCount));
BigDecimal rawProfit = finalRevenue.subtract(totalCost).subtract(royaltyFee);
BigDecimal netProfit = rawProfit.signum() < 0 ? BigDecimal.ZERO : rawProfit;
BigDecimal lossTransferred = rawProfit.signum() < 0 ? rawProfit.abs() : BigDecimal.ZERO;
```

- [ ] **Step 4: Add controller test coverage for `GET /api/clearing/ventures` and `POST /api/clearing/execute` envelope semantics**

```java
mockMvc.perform(get("/api/clearing/ventures"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data").exists());
```

- [ ] **Step 5: Run the service and controller tests to verify they pass**

Run: `mvn -Dtest=FinanceClearingServiceTest,FinanceWorkbenchControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceClearingService.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceWorkbenchController.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceClearingExecuteRequest.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceClearingExecuteResponse.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceClearingServiceTest.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceWorkbenchControllerTest.java
git commit -m "feat: stabilize finance clearing execution"
```

### Task 5: Stabilize dividend prepare/list/confirm with bank-balance assertion

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceDividendService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceDividendController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceDividendPrepareRequest.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceDividendPrepareResponse.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceDividendListResponse.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceDividendConfirmResponse.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceDividendServiceTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceDividendControllerTest.java`

- [ ] **Step 1: Write the failing tests for duplicate pending prevention and insufficient-balance rejection**

```java
@Test
void confirm_rejectsWhenPendingAmountExceedsLatestBankBalance() {
    assertThatThrownBy(() -> service.confirm(request, "user-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bank balance");
}
```

- [ ] **Step 2: Run the dividend tests to verify they fail**

Run: `mvn -Dtest=FinanceDividendServiceTest test`
Expected: FAIL on missing assertion, duplicate control, or response mapping.

- [ ] **Step 3: Implement prepare/list/confirm transaction rules with PENDING uniqueness and CONFIRMED idempotency**

```java
if (pendingTotal.compareTo(latestBankBalance) > 0) {
    throw new IllegalArgumentException("pending dividend amount exceeds current bank balance");
}
```

- [ ] **Step 4: Add controller test coverage for status filtering and confirm response shape**

```java
mockMvc.perform(get("/api/dividend/list").param("status", "PENDING"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.items").isArray());
```

- [ ] **Step 5: Run service and controller tests to verify they pass**

Run: `mvn -Dtest=FinanceDividendServiceTest,FinanceDividendControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceDividendService.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceDividendController.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceDividendPrepareRequest.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceDividendPrepareResponse.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceDividendListResponse.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceDividendConfirmResponse.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceDividendServiceTest.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceDividendControllerTest.java
git commit -m "feat: stabilize finance dividend workflow"
```

### Task 6: Stabilize wallets and transaction-audit query semantics

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceReportingService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceWalletOverviewResponse.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceTransactionListResponse.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceReportingServiceTest.java`

- [ ] **Step 1: Write the failing tests for wallet summary rows and transaction audit fields**

```java
@Test
void getTransactions_returnsSourceAuditFieldsAndOwner() {
    FinanceTransactionListResponse response = service.getTransactions(100, null, null, null, null);
    assertThat(response.getItems()).allSatisfy(item -> {
        assertThat(item.getAudit()).isNotNull();
        assertThat(item.getOwner()).isNotNull();
    });
}
```

- [ ] **Step 2: Run the reporting test to verify it fails**

Run: `mvn -Dtest=FinanceReportingServiceTest test`
Expected: FAIL until DTO fields and mapping match the wallet requirements.

- [ ] **Step 3: Align wallet overview and transaction list DTOs with the fields defined in `ambition.md`**

```java
public static class TransactionRow {
    private String transactionType;
    private BigDecimal amount;
    private FinanceAuditRef audit;
    private String remark;
}
```

- [ ] **Step 4: Update reporting service mapping to preserve role, totals, source table, source id, remark, and timestamps**

- [ ] **Step 5: Run the reporting test to verify it passes**

Run: `mvn -Dtest=FinanceReportingServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceReportingService.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceWalletOverviewResponse.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceTransactionListResponse.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceReportingServiceTest.java
git commit -m "feat: align finance wallet query contracts"
```

### Task 7: Stabilize P1 cost batch execution and preview

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceCostBatchService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceLedgerMonthRequest.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceCostBatchRunResponse.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceCostPreviewResponse.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceWorkbenchController.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceCostBatchServiceTest.java`

- [ ] **Step 1: Write the failing tests for standard-hour and multiplier formulas plus ledger-month uniqueness**

```java
@Test
void runBatch_appliesStandardHoursAndMultiplier() {
    FinanceCostBatchRunResponse response = service.runBatch("2026-03");
    assertThat(response.getGeneratedCount()).isGreaterThanOrEqualTo(0);
}
```

- [ ] **Step 2: Run the cost batch tests to verify they fail**

Run: `mvn -Dtest=FinanceCostBatchServiceTest test`
Expected: FAIL until formula and duplicate-run rules are explicit.

- [ ] **Step 3: Implement `160`-hour and `2.0` multiplier formulas, plus clear same-month idempotency behavior**

```java
BigDecimal baseAmount = baseMonthlyCost.multiply(hoursSpent).divide(new BigDecimal("160"), 2, HALF_UP);
BigDecimal finalCost = baseAmount.multiply(new BigDecimal("2.0")).setScale(2, HALF_UP);
```

- [ ] **Step 4: Ensure preview remains ledger-month-aware in both controller and DTO contract**

- [ ] **Step 5: Run the cost batch tests to verify they pass**

Run: `mvn -Dtest=FinanceCostBatchServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceCostBatchService.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceLedgerMonthRequest.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceCostBatchRunResponse.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceCostPreviewResponse.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceWorkbenchController.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceCostBatchServiceTest.java
git commit -m "feat: stabilize finance cost batch rules"
```

### Task 8: Stabilize P2 AI and RAG as read-only finance intelligence

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceAiContextService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceAiService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceRagService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAiController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceAiChatResponse.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceRagQueryResponse.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAiControllerTest.java`

- [ ] **Step 1: Write the failing test for AI-first, RAG-fallback, read-only behavior**

```java
@Test
void chat_fallsBackToRagWhenAiFails() {
    FinanceAiChatResponse response = service.chat(request);
    assertThat(response.getAnswer()).isNotBlank();
    assertThat(response.getFallbackUsed()).isTrue();
}
```

- [ ] **Step 2: Run the AI test to verify it fails**

Run: `mvn -Dtest=FinanceAiControllerTest test`
Expected: FAIL until fallback metadata and controller mapping are aligned.

- [ ] **Step 3: Keep context assembly read-only and include only finance-approved sources**

```java
FinanceAiContext context = FinanceAiContext.builder()
    .clearing(clearingSummary)
    .wallets(walletSummary)
    .adjustments(adjustmentSummary)
    .bankSnapshot(bankSnapshot)
    .build();
```

- [ ] **Step 4: Implement clear fallback metadata in AI response and keep RAG push/query isolated from write-path services**

- [ ] **Step 5: Run the AI controller test to verify it passes**

Run: `mvn -Dtest=FinanceAiControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceAiContextService.java erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceAiService.java erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceRagService.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAiController.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceAiChatResponse.java erp-backend/src/main/java/com/smartlab/erp/finance/dto/FinanceRagQueryResponse.java erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceAiControllerTest.java
git commit -m "feat: stabilize finance ai and rag flows"
```

## Chunk 2: Frontend integration and verification in `lab-erp-demo`

### Task 9: Align shared API adapters and finance stores with backend contracts

**Files:**
- Modify: `lab-erp-demo/src/api/finance/overview.js`
- Modify: `lab-erp-demo/src/api/finance/workbench.js`
- Modify: `lab-erp-demo/src/api/finance/wallets.js`
- Modify: `lab-erp-demo/src/stores/financeOverviewStore.js`
- Modify: `lab-erp-demo/src/stores/financeWorkbenchStore.js`
- Modify: `lab-erp-demo/src/stores/financeWalletStore.js`
- Modify: `lab-erp-demo/src/stores/financeAiStore.js`
- Modify: `lab-erp-demo/src/utils/financeAdapters.js`
- Modify: `lab-erp-demo/src/utils/financeEnums.js`
- Create: `lab-erp-demo/scripts/finance-adapters.test.mjs`
- Create: `lab-erp-demo/scripts/finance-workbench-store.test.mjs`

- [ ] **Step 1: Write failing node-based contract tests for envelope handling and store normalization**

```js
import assert from 'node:assert/strict'
import { unwrapFinanceEnvelope } from '../src/utils/financeAdapters.js'

const envelope = unwrapFinanceEnvelope({ data: { success: true, message: 'ok', data: { items: [] }, traceId: 't-1' } }, [])
assert.deepEqual(envelope.data.items, [])
```

- [ ] **Step 2: Run the explicit contract tests to confirm mismatch**

Run: `node ./scripts/finance-adapters.test.mjs && node ./scripts/finance-workbench-store.test.mjs`
Expected: FAIL until adapters and stores match the backend envelope and payload shapes.

- [ ] **Step 3: Align API functions and store state with backend contract names and list/mutation semantics**

```js
export const getCostBatchPreview = (ventureId, ledgerMonth) =>
  request.get(`/api/batch/preview/${ventureId}`, { params: { ledgerMonth } })
```

- [ ] **Step 4: Normalize high-risk mutation results and list payloads in one place only (`financeAdapters.js`)**

- [ ] **Step 5: Run the contract tests and frontend build to verify the store/api layer works**

Run: `node ./scripts/finance-adapters.test.mjs && node ./scripts/finance-workbench-store.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add lab-erp-demo/src/api/finance/overview.js lab-erp-demo/src/api/finance/workbench.js lab-erp-demo/src/api/finance/wallets.js lab-erp-demo/src/stores/financeOverviewStore.js lab-erp-demo/src/stores/financeWorkbenchStore.js lab-erp-demo/src/stores/financeWalletStore.js lab-erp-demo/src/stores/financeAiStore.js lab-erp-demo/src/utils/financeAdapters.js lab-erp-demo/src/utils/financeEnums.js lab-erp-demo/scripts/finance-adapters.test.mjs lab-erp-demo/scripts/finance-workbench-store.test.mjs
git commit -m "feat: align finance frontend stores with backend contracts"
```

### Task 10: Implement the P0 overview and wallet surfaces

**Files:**
- Modify: `lab-erp-demo/src/views/finance/FinanceOverviewView.vue`
- Modify: `lab-erp-demo/src/views/finance/FinanceWalletsView.vue`
- Modify: `lab-erp-demo/src/utils/financeFormatters.js`
- Create: `lab-erp-demo/src/components/finance/FinanceMetricCard.vue`
- Create: `lab-erp-demo/src/components/finance/FinanceStatusTag.vue`
- Create: `lab-erp-demo/src/components/finance/FinanceEmptyState.vue`
- Create: `lab-erp-demo/src/components/finance/FinanceErrorState.vue`

- [ ] **Step 1: Add a failing UI assertion by rendering required KPI/reconciliation/wallet fields locally**

```vue
<FinanceMetricCard v-for="kpi in overview.kpis" :key="kpi.key" :label="kpi.label" :value="kpi.value" />
```

- [ ] **Step 2: Run the frontend build to verify missing imports or props fail fast**

Run: `npm run build`
Expected: FAIL until the new components and bindings exist.

- [ ] **Step 3: Implement overview page sections for KPI cards, risk table, cash flow, reconciliation, and bank snapshot form**

- [ ] **Step 4: Implement wallet summary and transaction audit tables using backend-provided data only**

- [ ] **Step 5: Run the frontend build to verify the pages compile**

Run: `npm run build`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add lab-erp-demo/src/views/finance/FinanceOverviewView.vue lab-erp-demo/src/views/finance/FinanceWalletsView.vue lab-erp-demo/src/utils/financeFormatters.js lab-erp-demo/src/components/finance/FinanceMetricCard.vue lab-erp-demo/src/components/finance/FinanceStatusTag.vue lab-erp-demo/src/components/finance/FinanceEmptyState.vue lab-erp-demo/src/components/finance/FinanceErrorState.vue
git commit -m "feat: implement finance overview and wallet pages"
```

### Task 10A: Add overview-to-clearing and overview-to-cost-batch linkage polish

**Files:**
- Modify: `lab-erp-demo/src/views/finance/FinanceOverviewView.vue`
- Modify: `lab-erp-demo/src/router/financeRoutes.js`
- Create: `lab-erp-demo/scripts/finance-overview-links.test.mjs`

- [ ] **Step 1: Write a failing node-based view-model test for overview action links**

```js
import assert from 'node:assert/strict'
assert.equal(buildOverviewLink('clearing', 'venture-1'), '/finance/clearing')
```

- [ ] **Step 2: Run the link test to verify it fails**

Run: `node ./scripts/finance-overview-links.test.mjs`
Expected: FAIL until the overview exposes stable actions for clearing and cost batch pages.

- [ ] **Step 3: Add overview actions that route users from risk cards to clearing and from trend/reporting sections to cost batch workbench**

- [ ] **Step 4: Run the link test and frontend build to verify they pass**

Run: `node ./scripts/finance-overview-links.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/src/views/finance/FinanceOverviewView.vue lab-erp-demo/src/router/financeRoutes.js lab-erp-demo/scripts/finance-overview-links.test.mjs
git commit -m "feat: connect finance overview actions to workbench routes"
```

### Task 11: Implement the P0 adjustment surface

**Files:**
- Modify: `lab-erp-demo/src/views/finance/AdjustmentCenterView.vue`
- Create: `lab-erp-demo/src/components/finance/FinanceConfirmDialog.vue`

- [ ] **Step 1: Add failing page bindings for create/list and high-risk confirmation flows**

```vue
<FinanceConfirmDialog v-model:open="confirmOpen" title="Confirm adjustment creation" @confirm="onConfirmAdjustment" />
```

- [ ] **Step 2: Run the frontend build to verify the missing dialog/component wiring fails**

Run: `npm run build`
Expected: FAIL until high-risk actions are wired consistently.

- [ ] **Step 3: Implement adjustment form/list UX with explicit DEBIT/CREDIT options and backend error rendering**

- [ ] **Step 4: Run the frontend build to verify the page compiles**

Run: `npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/src/views/finance/AdjustmentCenterView.vue lab-erp-demo/src/components/finance/FinanceConfirmDialog.vue
git commit -m "feat: implement finance adjustment page"
```

### Task 11A: Implement the P0 clearing surface

**Files:**
- Modify: `lab-erp-demo/src/views/finance/ClearingCenterView.vue`
- Modify: `lab-erp-demo/src/stores/financeWorkbenchStore.js`

- [ ] **Step 1: Add failing page bindings for venture selection and clearing result cards**

```vue
<el-select v-model="selectedVentureId">
  <el-option v-for="venture in clearingVentures" :key="venture.ventureId" :label="venture.displayName" :value="venture.ventureId" />
</el-select>
```

- [ ] **Step 2: Run the frontend build to verify the page fails until wired**

Run: `npm run build`
Expected: FAIL until the contract-driven bindings exist.

- [ ] **Step 3: Implement clearing submit and result rendering without duplicating formulas in Vue**

- [ ] **Step 4: Run the frontend build to verify the page compiles**

Run: `npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/src/views/finance/ClearingCenterView.vue lab-erp-demo/src/stores/financeWorkbenchStore.js
git commit -m "feat: implement finance clearing page"
```

### Task 11B: Implement the P0 dividend surface

**Files:**
- Modify: `lab-erp-demo/src/views/finance/DividendCenterView.vue`
- Modify: `lab-erp-demo/src/stores/financeWorkbenchStore.js`

- [ ] **Step 1: Add failing page bindings for dividend prepare/list/confirm flow**

```vue
<FinanceConfirmDialog v-model:open="confirmOpen" title="Confirm dividend payout" @confirm="onConfirmDividend" />
```

- [ ] **Step 2: Run the frontend build to verify the page fails until wired**

Run: `npm run build`
Expected: FAIL until the confirmation and list state are connected.

- [ ] **Step 3: Implement prepare/list/confirm UX with second confirmation before confirm submit**

- [ ] **Step 4: Run the frontend build to verify the page compiles**

Run: `npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/src/views/finance/DividendCenterView.vue lab-erp-demo/src/stores/financeWorkbenchStore.js lab-erp-demo/src/components/finance/FinanceConfirmDialog.vue
git commit -m "feat: implement finance dividend page"
```

### Task 12: Implement the P1 cost batch and P2 AI/RAG surfaces

**Files:**
- Modify: `lab-erp-demo/src/views/finance/CostBatchView.vue`
- Modify: `lab-erp-demo/src/views/finance/FinanceAiChatView.vue`
- Modify: `lab-erp-demo/src/views/finance/RagSearchView.vue`
- Modify: `lab-erp-demo/src/stores/financeAiStore.js`

- [ ] **Step 1: Add failing UI bindings for ledger-month preview, progressive rendering, fallback messaging, and shortcut prompts**

```vue
<p v-if="fallbackUsed">AI unavailable, showing finance retrieval answer.</p>
<button v-for="prompt in shortcutPrompts" :key="prompt" @click="runPrompt(prompt)">{{ prompt }}</button>
```

- [ ] **Step 2: Run the frontend build to verify missing bindings fail**

Run: `npm run build`
Expected: FAIL until the new state is consumed by the views.

- [ ] **Step 3: Implement cost batch run and preview UI using the ledger-month-aware contract**

- [ ] **Step 4: Implement AI chat with streaming or progressive rendering behavior and explicit fallback messaging**

- [ ] **Step 5: Implement RAG search with shortcut prompts and read-only framing**

- [ ] **Step 6: Run the frontend build to verify the pages compile**

Run: `npm run build`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add lab-erp-demo/src/views/finance/CostBatchView.vue lab-erp-demo/src/views/finance/FinanceAiChatView.vue lab-erp-demo/src/views/finance/RagSearchView.vue lab-erp-demo/src/stores/financeAiStore.js
git commit -m "feat: implement finance cost batch and ai pages"
```

### Task 13: End-to-end verification of backend and frontend phases

**Files:**
- Modify: `docs/superpowers/plans/2026-03-12-finance-console-rebuild-plan.md`

- [ ] **Step 1: Run targeted backend tests for finance services and controllers**

Run: `mvn -Dtest=FinanceReportingServiceTest,FinanceAdjustmentServiceTest,FinanceClearingServiceTest,FinanceDividendServiceTest,FinanceCostBatchServiceTest,FinanceAdjustmentControllerTest,FinanceWorkbenchControllerTest,FinanceDividendControllerTest,FinanceAiControllerTest test`
Expected: PASS

- [ ] **Step 2: Run frontend build verification**

Run: `npm run build`
Expected: PASS

- [ ] **Step 3: Verify the concrete P0/P1/P2 page actions and expected results**

```text
P0 overview: KPI cards load, risk table loads, bank snapshot submit succeeds, reconciliation updates
P0 wallets: wallet rows render balances and totals, transaction table shows source table/id and remarks
P0 adjustments: invalid amount is rejected, valid DEBIT/CREDIT adjustment appears in audit list
P0 clearing: venture list loads, execute returns cost/royalty/profit result, loss path shows company transfer
P0 dividends: prepare creates PENDING rows, insufficient bank balance blocks confirm, success changes rows to CONFIRMED
P1 cost batch: same ledger month follows idempotency rule, preview reflects selected ledger month
P1 linkage: overview action links navigate to clearing and cost batch routes
P2 AI/RAG: AI response renders progressively, failure shows fallback message, shortcut prompt runs RAG-backed query
```

- [ ] **Step 4: Record the command outputs and page-verification evidence in the execution log or PR description**

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-03-12-finance-console-rebuild-plan.md
git commit -m "docs: track finance console verification progress"
```
