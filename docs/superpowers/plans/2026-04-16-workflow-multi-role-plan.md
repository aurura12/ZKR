# Workflow Multi-Role Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow the same user to appear multiple times in workflow selection flows when they hold multiple workflow roles, without changing system login authorization.

**Architecture:** Keep `sys_user` unchanged for login and global authority, add a canonical `workflow_member_role` mapping table for workflow-scoped role assignments, and migrate legacy workflow role data into the new table. Frontend selectors should render one entry per workflow-role pair, while backend read paths prefer the new table and fall back to legacy sources during the transition window.

**Tech Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, Vue 3, Pinia, Axios

---

### Task 1: Add the canonical workflow-role entity and repository

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/entity/WorkflowMemberRole.java`
- Create: `erp-backend/src/main/java/com/smartlab/erp/repository/WorkflowMemberRoleRepository.java`

- [ ] **Step 1: Write the failing test**

Create a repository test that inserts two rows for the same `workflow_type + workflow_id + user_id` with different roles and asserts both persist, while a duplicate `workflow_type + workflow_id + user_id + role` insert is rejected by the unique constraint.

```java
@Test
void allows_same_user_with_different_roles_but_rejects_duplicate_role() {
    WorkflowMemberRole data = repo.save(WorkflowMemberRole.builder()
            .workflowType("PROJECT")
            .workflowId("p-1")
            .userId("000027")
            .role("DATA")
            .build());

    WorkflowMemberRole dev = repo.save(WorkflowMemberRole.builder()
            .workflowType("PROJECT")
            .workflowId("p-1")
            .userId("000027")
            .role("DEV")
            .build());

    assertThat(data.getId()).isNotNull();
    assertThat(dev.getId()).isNotNull();

    assertThatThrownBy(() -> repo.saveAndFlush(WorkflowMemberRole.builder()
            .workflowType("PROJECT")
            .workflowId("p-1")
            .userId("000027")
            .role("DATA")
            .build())).isInstanceOf(DataIntegrityViolationException.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=WorkflowMemberRoleRepositoryTest -v`
Expected: FAIL with missing entity/repository or missing table mapping.

- [ ] **Step 3: Write minimal implementation**

```java
@Entity
@Table(name = "workflow_member_role", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"workflow_type", "workflow_id", "user_id", "role"})
})
public class WorkflowMemberRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "workflow_type", nullable = false, length = 32)
    private String workflowType;
    @Column(name = "workflow_id", nullable = false, length = 64)
    private String workflowId;
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    @Column(name = "role", nullable = false, length = 64)
    private String role;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

Repository helpers:

```java
List<WorkflowMemberRole> findByWorkflowTypeAndWorkflowId(String workflowType, String workflowId);
List<WorkflowMemberRole> findByUserId(String userId);
Optional<WorkflowMemberRole> findByWorkflowTypeAndWorkflowIdAndUserIdAndRole(String workflowType, String workflowId, String userId, String role);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=WorkflowMemberRoleRepositoryTest -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/entity/WorkflowMemberRole.java erp-backend/src/main/java/com/smartlab/erp/repository/WorkflowMemberRoleRepository.java erp-backend/src/test/java/.../WorkflowMemberRoleRepositoryTest.java
git commit -m "feat: add workflow member role mapping"
```

### Task 2: Migrate legacy workflow role data into the new table

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/config/WorkflowMemberRoleMigration.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/config/UserSchemaMigration.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/ProjectService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/ProductFlowService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/ResearchFlowService.java`

- [ ] **Step 1: Write the failing test**

Create a migration service test that seeds legacy project/product/research role data and asserts the new migration writes rows into `workflow_member_role`.

```java
@Test
void migrates_legacy_workflow_roles_into_mapping_table() {
    migration.run();
    assertThat(repo.findByWorkflowTypeAndWorkflowId("PROJECT", "p-1")).hasSize(2);
    assertThat(repo.findByWorkflowTypeAndWorkflowId("PRODUCT", "prod-1")).hasSize(3);
    assertThat(repo.findByWorkflowTypeAndWorkflowId("RESEARCH", "r-1")).hasSize(4);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=WorkflowMemberRoleMigrationTest -v`
Expected: FAIL because migration service does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Implement a migration runner that reads:

- `sys_project_member`
- `ResearchProjectProfile`
- product owner fields / team membership sources

and writes normalized role rows into `workflow_member_role`.

Add dual-write hooks in the workflow services so that any role assignment written to legacy flow tables is also written to `workflow_member_role`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=WorkflowMemberRoleMigrationTest -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/config/WorkflowMemberRoleMigration.java erp-backend/src/main/java/com/smartlab/erp/config/UserSchemaMigration.java erp-backend/src/main/java/com/smartlab/erp/service/ProjectService.java erp-backend/src/main/java/com/smartlab/erp/service/ProductFlowService.java erp-backend/src/main/java/com/smartlab/erp/service/ResearchFlowService.java
git commit -m "feat: migrate workflow roles into mapping table"
```

### Task 3: Add read-new-first service layer and workflow selection API

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/service/WorkflowMemberRoleService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/ProjectService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/ProductFlowService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/ResearchFlowService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/ProjectController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/ProductFlowController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/ResearchFlowController.java`

- [ ] **Step 1: Write the failing test**

Create a service test that seeds the new mapping table and asserts the selection API returns one item per `userId + role` pair, with display fields resolved from `sys_user`.

```java
@Test
void returns_one_selection_row_per_user_role_pair() {
    List<WorkflowMemberSelectionDto> items = service.listWorkflowMembers("PROJECT", "p-1");
    assertThat(items).extracting(WorkflowMemberSelectionDto::getUserId, WorkflowMemberSelectionDto::getRole)
            .containsExactly(tuple("000027", "DATA"), tuple("000027", "DEV"), tuple("000042", "RESEARCH"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=WorkflowMemberRoleServiceTest -v`
Expected: FAIL because service and DTO do not exist.

- [ ] **Step 3: Write minimal implementation**

Add a `WorkflowMemberRoleService` that:

```java
public List<WorkflowMemberSelectionDto> listWorkflowMembers(String workflowType, String workflowId) {
    List<WorkflowMemberRole> rows = repo.findByWorkflowTypeAndWorkflowId(workflowType, workflowId);
    if (!rows.isEmpty()) {
        return rows.stream().map(this::toDto).toList();
    }
    return legacyFallback(workflowType, workflowId);
}
```

Expose this through existing workflow controllers as a selection API for frontend member pickers.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=WorkflowMemberRoleServiceTest -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/service/WorkflowMemberRoleService.java erp-backend/src/main/java/com/smartlab/erp/controller/ProjectController.java erp-backend/src/main/java/com/smartlab/erp/controller/ProductFlowController.java erp-backend/src/main/java/com/smartlab/erp/controller/ResearchFlowController.java erp-backend/src/main/java/com/smartlab/erp/service/ProjectService.java erp-backend/src/main/java/com/smartlab/erp/service/ProductFlowService.java erp-backend/src/main/java/com/smartlab/erp/service/ResearchFlowService.java
git commit -m "feat: read workflow members from normalized roles"
```

### Task 4: Update workflow selection UIs to render duplicate user-role rows

**Files:**
- Modify: `lab-erp-demo/src/views/CreateResearchView.vue`
- Modify: `lab-erp-demo/src/views/ProjectDetail.vue`
- Modify: `lab-erp-demo/src/views/CreateDeliveryProjectView.vue` if it renders member selectors
- Modify: `lab-erp-demo/src/views/CreateProject.vue` if it renders product member selectors

- [ ] **Step 1: Write the failing test**

Create a frontend test or component-level assertion that the options list renders `张三 / DATA` and `张三 / DEV` as separate options when the API returns two rows with the same `userId` and different roles.

```vue
expect(screen.getByText('张三 / DATA')).toBeInTheDocument()
expect(screen.getByText('张三 / DEV')).toBeInTheDocument()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:unit -- WorkflowMemberRoleSelect.spec.js`
Expected: FAIL because the UI still deduplicates by `userId`.

- [ ] **Step 3: Write minimal implementation**

Change selectors to consume normalized role rows and use a composite key such as `userId-role` for rendering. Do not deduplicate by `userId` alone.

```javascript
const optionKey = row => `${row.userId}-${row.role}`
const optionLabel = row => `${row.name} / ${row.role}`
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:unit -- WorkflowMemberRoleSelect.spec.js`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/src/views/CreateResearchView.vue lab-erp-demo/src/views/ProjectDetail.vue lab-erp-demo/src/views/CreateDeliveryProjectView.vue lab-erp-demo/src/views/CreateProject.vue
git commit -m "feat: show workflow roles per user in selectors"
```

### Task 5: Verify read-new-first fallback and rollout behavior

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/WorkflowMemberRoleService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/config/WorkflowMemberRoleMigration.java`
- Test: backend integration tests for fallback and migration counts

- [ ] **Step 1: Write the failing test**

Write tests proving that if `workflow_member_role` has rows for a workflow, they are returned first, and if it does not, the service falls back to the legacy source once.

```java
@Test
void prefers_new_table_then_falls_back_to_legacy_only_when_needed() {
    assertThat(service.listWorkflowMembers("RESEARCH", "r-legacy")).hasSize(4); // from legacy fallback
    assertThat(service.listWorkflowMembers("RESEARCH", "r-new")).hasSize(4); // from new table
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=WorkflowMemberRoleServiceFallbackTest -v`
Expected: FAIL because fallback behavior is not implemented yet.

- [ ] **Step 3: Write minimal implementation**

Implement fallback lookup and logging of which source served the request.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=WorkflowMemberRoleServiceFallbackTest -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/service/WorkflowMemberRoleService.java erp-backend/src/main/java/com/smartlab/erp/config/WorkflowMemberRoleMigration.java
git commit -m "feat: add read fallback for workflow roles"
```

### Task 6: Build and verify backend/frontend

**Files:**
- Build: backend and frontend containers
- Verify: route behavior, member duplication display, and no login auth changes

- [ ] **Step 1: Run backend tests**

Run: `mvn test`
Expected: PASS for workflow role tests and existing auth tests.

- [ ] **Step 2: Run frontend tests / build**

Run: `npm run build`
Expected: PASS.

- [ ] **Step 3: Manual verification**

Check that a workflow selection screen can render the same person multiple times with different role labels, while `/erp-login`, `@PreAuthorize`, and JWT authority behavior remain unchanged.

- [ ] **Step 4: Commit**

```bash
git add .
git commit -m "feat: support multi-role workflow member selection"
```

## Spec Coverage Check

- `sys_user` remains unchanged: covered by the non-goals and data model sections.
- Unified workflow-role mapping table: covered by Task 1.
- Historical migration: covered by Task 2.
- Dual-write and read-new-first: covered by Tasks 2 and 5.
- Frontend repeated display by role: covered by Task 4.
- No auth changes: covered by the non-goals and backend design.
