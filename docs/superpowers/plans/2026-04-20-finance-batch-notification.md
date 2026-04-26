# Finance Batch Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Notify Zhang Qi after every finance cost batch execution, using internal message first and SMTP email as fallback, without changing batch semantics or login/auth behavior.

**Architecture:** Add one finance-specific notification service that resolves the fixed `zhangqi` recipient and encapsulates the internal-message-first, email-fallback delivery policy. Call that service from `FinanceCostBatchService.runBatch(...)` in both success and failure paths so scheduled, manual, and startup backfill entrypoints all share the same notification behavior.

**Tech Stack:** Spring Boot, Spring Data JPA, JavaMailSender, JUnit 5, Mockito

---

### Task 1: Add finance batch notification service

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceBatchNotificationService.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceBatchNotificationServiceTest.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/MailService.java`

- [ ] **Step 1: Write the failing test**

Create a focused test that proves the notification service:

- resolves `username = "zhangqi"`
- sends internal message on success
- falls back to SMTP email when internal message throws
- never rethrows notification delivery failures

Use a test like this:

```java
@Test
void notifySuccess_usesInternalMessageFirst() {
    User zhangqi = User.builder()
            .userId("000001")
            .username("zhangqi")
            .name("张琦")
            .email("zhangqi@example.com")
            .build();
    when(userRepository.findByUsername("zhangqi")).thenReturn(Optional.of(zhangqi));

    notificationService.notifyBatchSuccess("2026-04", 55L, 12);

    verify(internalMessageService).sendMessage(eq("000001"), eq("FINANCE_BATCH_RESULT"), contains("成功"), contains("2026-04"), isNull());
    verify(mailService, never()).sendFinanceBatchResult(anyString(), anyString(), anyString());
}
```

Add another test for fallback:

```java
@Test
void notifyFailure_fallsBackToEmailWhenInternalMessageFails() {
    User zhangqi = User.builder()
            .userId("000001")
            .username("zhangqi")
            .name("张琦")
            .email("zhangqi@example.com")
            .build();
    when(userRepository.findByUsername("zhangqi")).thenReturn(Optional.of(zhangqi));
    doThrow(new RuntimeException("message failed"))
            .when(internalMessageService)
            .sendMessage(anyString(), anyString(), anyString(), anyString(), any());

    notificationService.notifyBatchFailure("2026-04", new RuntimeException("batch failed"));

    verify(mailService).sendFinanceBatchResult(eq("zhangqi@example.com"), contains("失败"), contains("2026-04"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=FinanceBatchNotificationServiceTest test
```

Expected: FAIL because the finance notification service and mail helper do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `FinanceBatchNotificationService` with a small API like:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceBatchNotificationService {

    private static final String FIXED_USERNAME = "zhangqi";
    private static final String MESSAGE_TYPE = "FINANCE_BATCH_RESULT";

    private final UserRepository userRepository;
    private final InternalMessageService internalMessageService;
    private final MailService mailService;

    public void notifyBatchSuccess(String ledgerMonth, Long batchId, int generatedRecordCount) {
        String title = "财务跑批成功";
        String content = "ledgerMonth=" + ledgerMonth + ", batchId=" + batchId + ", generatedRecordCount=" + generatedRecordCount;
        deliver(title, content);
    }

    public void notifyBatchFailure(String ledgerMonth, RuntimeException ex) {
        String title = "财务跑批失败";
        String content = "ledgerMonth=" + ledgerMonth + ", error=" + (ex == null ? "unknown" : ex.getMessage());
        deliver(title, content);
    }

    private void deliver(String title, String content) {
        Optional<User> user = userRepository.findByUsername(FIXED_USERNAME);
        if (user.isEmpty()) {
            log.error("Finance batch notification skipped: fixed user '{}' not found", FIXED_USERNAME);
            return;
        }

        User recipient = user.get();
        try {
            internalMessageService.sendMessage(recipient.getUserId(), MESSAGE_TYPE, title, content, null);
            return;
        } catch (RuntimeException ex) {
            log.error("Internal finance batch notification failed for {}", FIXED_USERNAME, ex);
        }

        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            log.error("Finance batch email fallback skipped: recipient '{}' has no email", FIXED_USERNAME);
            return;
        }

        try {
            mailService.sendFinanceBatchResult(recipient.getEmail(), title, content);
        } catch (RuntimeException ex) {
            log.error("Finance batch email fallback failed for {}", FIXED_USERNAME, ex);
        }
    }
}
```

Add a small reusable mail helper to `MailService`:

```java
public void sendFinanceBatchResult(String toEmail, String subject, String content) {
    if (fromAddress == null || fromAddress.isBlank()) {
        throw new BusinessException("邮件服务未配置，请先设置发信邮箱");
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(toEmail);
    message.setSubject(subject);
    message.setText(content);

    try {
        mailSender.send(message);
    } catch (MailException ex) {
        throw new BusinessException("财务跑批通知邮件发送失败：" + ex.getMessage());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=FinanceBatchNotificationServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceBatchNotificationService.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceBatchNotificationServiceTest.java erp-backend/src/main/java/com/smartlab/erp/service/MailService.java
git commit -m "feat: add finance batch notification service"
```

### Task 2: Hook notifications into batch completion paths

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceCostBatchService.java`
- Modify: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceCostBatchServiceTest.java`

- [ ] **Step 1: Write the failing test**

Extend `FinanceCostBatchServiceTest` with one success case and one failure case that verify notifications happen after batch completion.

First align the test fixture with the current `FinanceCostBatchService` constructor dependencies if the file is stale, then add a mock for `FinanceBatchNotificationService` and assert behavior like:

```java
@Test
void runBatch_notifiesSuccessAfterBatchCompletes() {
    // existing fixture and repository stubs

    FinanceCostBatchRunResponse response = financeCostBatchService.runBatch("2026-03");

    verify(financeBatchNotificationService).notifyBatchSuccess("2026-03", response.getBatchId(), response.getGeneratedRecordCount());
}
```

And for failure:

```java
@Test
void runBatch_notifiesFailureBeforeRethrowingOriginalException() {
    when(costBatchRepository.save(any(FinanceCostBatch.class))).thenAnswer(invocation -> {
        FinanceCostBatch batch = invocation.getArgument(0);
        if (batch.getId() == null) {
            batch.setId(99L);
        }
        return batch;
    });
    when(projectRepository.findAll()).thenThrow(new RuntimeException("boom"));

    RuntimeException error = assertThrows(RuntimeException.class,
            () -> financeCostBatchService.runBatch("2026-03", true));

    assertEquals("boom", error.getMessage());
    verify(financeBatchNotificationService).notifyBatchFailure(eq("2026-03"), any(RuntimeException.class));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=FinanceCostBatchServiceTest test
```

Expected: FAIL because `FinanceCostBatchService` does not call the notification service yet.

- [ ] **Step 3: Write minimal implementation**

Inject `FinanceBatchNotificationService` into `FinanceCostBatchService` and call it in the success and failure paths.

Use a pattern like:

```java
private final FinanceBatchNotificationService financeBatchNotificationService;
```

In the success path, just before returning:

```java
FinanceCostBatchRunResponse response = FinanceCostBatchRunResponse.builder()
        .batchId(batch.getId())
        .ledgerMonth(batch.getLedgerMonth())
        .status(batch.getStatus())
        .ventureCount(projects.size())
        .generatedRecordCount(generatedRecordCount)
        .totalSettlementCost(FinanceAmounts.scale(totalSettlementCost))
        .reusedExistingBatch(false)
        .build();
financeBatchNotificationService.notifyBatchSuccess(ledgerMonth, response.getBatchId(), response.getGeneratedRecordCount());
return response;
```

In the failure path, after persisting failed batch state but before rethrowing:

```java
financeBatchNotificationService.notifyBatchFailure(ledgerMonth, ex);
throw ex;
```

Do not change scheduler code or controller code.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=FinanceCostBatchServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/finance/service/FinanceCostBatchService.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceCostBatchServiceTest.java
git commit -m "feat: notify zhangqi after finance batch completion"
```

### Task 3: Verify full notification behavior remains isolated from auth and entrypoints

**Files:**
- Modify: `erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceWorkbenchControllerTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceBatchNotificationServiceTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceCostBatchServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add or extend controller-level tests only if needed to prove the manual endpoint still delegates to `FinanceCostBatchService.runBatch(...)` unchanged.

For example:

```java
@Test
void runCostBatch_endpointStillDelegatesToService() throws Exception {
    when(financeCostBatchService.runBatch(anyString(), anyBoolean())).thenReturn(FinanceCostBatchRunResponse.builder().build());

    mockMvc.perform(post("/api/batch/run_cost")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" +
                    "\"ledger_month\":\"2026-03\"," +
                    "\"rerun_existing_month\":true" +
                    "}"))
            .andExpect(status().isOk());

    verify(financeCostBatchService).runBatch("2026-03", true);
}
```

If this test already exists and passes unchanged, skip creating a new one and move directly to verification.

- [ ] **Step 2: Run verification to confirm current behavior**

Run:

```bash
docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=FinanceWorkbenchControllerTest,FinanceBatchNotificationServiceTest,FinanceCostBatchServiceTest test
```

Expected: all selected tests pass, proving the notification path is added without changing controller entrypoints or auth behavior.

- [ ] **Step 3: Write minimal implementation**

No extra production code should be needed here if Task 1 and Task 2 were done correctly. Only adjust tests if a controller assertion needs to be updated to match current request DTO names.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=FinanceWorkbenchControllerTest,FinanceBatchNotificationServiceTest,FinanceCostBatchServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/test/java/com/smartlab/erp/finance/controller/FinanceWorkbenchControllerTest.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceBatchNotificationServiceTest.java erp-backend/src/test/java/com/smartlab/erp/finance/service/FinanceCostBatchServiceTest.java
git commit -m "test: verify finance batch notification flow"
```
