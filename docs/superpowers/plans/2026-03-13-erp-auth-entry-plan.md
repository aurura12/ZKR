# ERP Auth Entry Implementation Plan

> Historical note (2026-03-15): this plan has been executed. ERP auth entry now exists at `/erp-login`, with shared auth components, ERP-specific storage, and domain-aware redirects. The repo is now in maintenance/update mode; keep this file as an execution record, not as a pending task list.

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated ERP login/register flip-card page at `/erp-login`, restore ERP-domain registration through shared auth endpoints, and keep ERP and finance auth flows isolated in both backend and frontend.

**Architecture:** Reuse the shared `/api/auth/*` backend endpoints and the existing finance-auth isolation foundation, but restore domain-aware registration on the backend and introduce an ERP-specific frontend auth page with ERP-scoped storage and redirects. Avoid copying the current finance login page wholesale by extracting shared auth page structure where practical.

**Tech Stack:** Spring Boot 3.2, Spring Security, Spring MVC, Spring Data JPA, JUnit; Vue 3, Pinia, Vue Router, Element Plus, Vite

---

## File Structure Map

### Backend existing anchors in `erp-backend`

- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java` - restore domain-aware registration while preserving finance isolation
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/AuthController.java` - keep shared auth contract stable if request/response tests need updates
- Modify: `erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java` - finance/ERP registration and login domain tests
- Modify: `erp-backend/src/test/java/com/smartlab/erp/controller/AuthControllerTest.java` - shared auth endpoint contract tests

### Frontend existing anchors in `lab-erp-demo`

- Modify: `lab-erp-demo/src/views/LoginView.vue` - keep finance page stable, possibly extract shared auth-card shell pieces if necessary
- Modify: `lab-erp-demo/src/stores/userStore.js` - extend auth store to support ERP-scoped login/register/storage without breaking finance flow
- Modify: `lab-erp-demo/src/router/index.js` - add `/erp-login` and authenticated redirect behavior
- Modify: `lab-erp-demo/src/router/domainAccess.js` - support ERP login entry redirect rules if needed

### Frontend likely additions in `lab-erp-demo`

- Create: `lab-erp-demo/src/views/ErpLoginView.vue`
- Create: `lab-erp-demo/src/components/auth/AuthFlipCardShell.vue`
- Create: `lab-erp-demo/src/components/auth/AuthCredentialsForm.vue`
- Create: `lab-erp-demo/src/components/auth/AuthRegistrationForm.vue`
- Create: `lab-erp-demo/scripts/erp-login-view.test.mjs`
- Create: `lab-erp-demo/scripts/erp-auth-store.test.mjs`
- Create: `lab-erp-demo/scripts/erp-auth-route.test.mjs`

## Chunk 1: Backend ERP-domain registration restoration in `erp-backend`

### Task 1: Restore domain-aware registration for shared auth endpoints

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java`
- Modify: `erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java`

- [ ] **Step 1: Write failing backend tests for ERP registration persisting `ERP` and finance registration still persisting `FINANCE`**

```java
@Test
void register_erpRequestPersistsErpDomain() {
    RegisterRequest request = RegisterRequest.builder()
        .username("erp-user")
        .password("secret123")
        .name("ERP User")
        .email("erp@example.com")
        .role("BUSINESS")
        .domain(AccountDomain.ERP)
        .build();

    authService.register(request);

    User saved = userRepository.findByUsername("erp-user").orElseThrow();
    assertThat(saved.getAccountDomain()).isEqualTo(AccountDomain.ERP);
}
```

- [ ] **Step 2: Run the backend auth service test to verify it fails**

Run: `mvn -Dtest=AuthServiceTest test`
Expected: FAIL because registration is currently forced to `FINANCE`.

- [ ] **Step 2A: Add a failing test for unsupported register domain and verify backend rejects it clearly**

```java
@Test
void register_rejectsUnsupportedDomain() {
    RegisterRequest request = RegisterRequest.builder().domain(null).build();
    assertThatThrownBy(() -> authService.register(request)).hasMessageContaining("账号域");
}
```

- [ ] **Step 3: Replace hardcoded finance registration with validated domain-aware registration in `AuthService.resolveRegisterDomain()`**

```java
private AccountDomain resolveRegisterDomain(RegisterRequest request) {
    if (request.getDomain() == null) {
        throw new BusinessException("注册失败：账号域不能为空");
    }
    if (request.getDomain() != AccountDomain.FINANCE && request.getDomain() != AccountDomain.ERP) {
        throw new BusinessException("注册失败：账号域非法");
    }
    return request.getDomain();
}
```

- [ ] **Step 4: Re-run the backend auth service test to verify it passes**

Run: `mvn -Dtest=AuthServiceTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java
git commit -m "feat: restore erp domain registration support"
```

### Task 2: Lock the shared auth HTTP contract for ERP registration and login

**Files:**
- Modify: `erp-backend/src/test/java/com/smartlab/erp/controller/AuthControllerTest.java`

- [ ] **Step 1: Write failing controller tests that prove `/api/auth/register` and `/api/auth/login` accept and bind `domain=ERP`, and that register rejects invalid/missing domain clearly at the HTTP boundary**

```java
mockMvc.perform(post("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"username":"erp-user","password":"secret123","name":"ERP User","email":"erp@example.com","role":"BUSINESS","domain":"ERP"}
        """))
    .andExpect(status().isOk());

ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
verify(authService).register(captor.capture());
assertThat(captor.getValue().getDomain()).isEqualTo(AccountDomain.ERP);
```

- [ ] **Step 2: Run the auth controller test to verify it fails**

Run: `mvn -Dtest=AuthControllerTest test`
Expected: FAIL until the ERP registration contract is covered correctly.

- [ ] **Step 3: Treat this as a controller-contract test task; change `AuthController.java` only if request binding or response assertions require it**

- [ ] **Step 4: Re-run the auth controller test to verify it passes**

Run: `mvn -Dtest=AuthControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/controller/AuthController.java erp-backend/src/test/java/com/smartlab/erp/controller/AuthControllerTest.java
git commit -m "test: lock shared auth contract for erp entry"
```

## Chunk 2: Dedicated ERP flip-card auth page in `lab-erp-demo`

### Task 3: Extract a shared flip-card auth shell for finance and ERP pages

**Files:**
- Create: `lab-erp-demo/src/components/auth/AuthFlipCardShell.vue`
- Create: `lab-erp-demo/src/components/auth/AuthCredentialsForm.vue`
- Create: `lab-erp-demo/src/components/auth/AuthRegistrationForm.vue`
- Modify: `lab-erp-demo/src/views/LoginView.vue`
- Test: `lab-erp-demo/scripts/finance-login-view.test.mjs`

- [ ] **Step 1: Write a failing node-based assertion that finance login still uses the shared flip-card structure after extraction**

```js
assert.match(source, /AuthFlipCardShell/, 'finance login should use shared auth flip-card shell')
```

- [ ] **Step 2: Run the finance login view test to verify it fails**

Run: `node ./scripts/finance-login-view.test.mjs`
Expected: FAIL until the shared auth shell is introduced.

- [ ] **Step 3: Extract the smallest shared auth shell/components needed without changing finance behavior**

- [ ] **Step 4: Re-run the finance login view test and frontend build to verify they pass**

Run: `node ./scripts/finance-login-view.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/src/components/auth/AuthFlipCardShell.vue lab-erp-demo/src/components/auth/AuthCredentialsForm.vue lab-erp-demo/src/components/auth/AuthRegistrationForm.vue lab-erp-demo/src/views/LoginView.vue lab-erp-demo/scripts/finance-login-view.test.mjs
git commit -m "refactor: extract shared auth flip-card components"
```

### Task 4: Add `/erp-login` with single-page flip-card login/register UX

**Files:**
- Create: `lab-erp-demo/src/views/ErpLoginView.vue`
- Create: `lab-erp-demo/scripts/erp-login-view.test.mjs`
- Modify: `lab-erp-demo/src/router/index.js`

- [ ] **Step 1: Write the failing node-based ERP page test for flip-card structure, ERP-specific copy, and `/erp-login` route presence**

```js
assert.match(source, /ERP系统登录/)
assert.match(source, /ERP系统注册/)
assert.match(source, /flip-wrap|AuthFlipCardShell/, 'ERP auth page should use flip-card interaction')
assert.match(source, /handleGoRegister|flipped/, 'ERP auth page should support CTA-driven face switching')
assert.match(routerSource, /path:\s*'\/erp-login'/)
```

- [ ] **Step 2: Run the ERP login view test to verify it fails**

Run: `node ./scripts/erp-login-view.test.mjs`
Expected: FAIL until the ERP page and route exist.

- [ ] **Step 3: Build `ErpLoginView.vue` using the shared flip-card shell and ERP-specific copy/config**

- [ ] **Step 4: Add `/erp-login` to the router and keep `/login` as finance-only**

- [ ] **Step 5: Re-run the ERP login view test and frontend build to verify they pass**

Run: `node ./scripts/erp-login-view.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add lab-erp-demo/src/views/ErpLoginView.vue lab-erp-demo/src/router/index.js lab-erp-demo/scripts/erp-login-view.test.mjs
git commit -m "feat: add dedicated erp auth page"
```

### Task 5: Add ERP-scoped auth actions and storage to the frontend store

**Files:**
- Modify: `lab-erp-demo/src/stores/userStore.js`
- Modify: `lab-erp-demo/src/views/ErpLoginView.vue`
- Modify: `lab-erp-demo/src/components/auth/AuthCredentialsForm.vue`
- Modify: `lab-erp-demo/src/components/auth/AuthRegistrationForm.vue`
- Create: `lab-erp-demo/scripts/erp-auth-store.test.mjs`

- [ ] **Step 1: Write the failing ERP auth store test for ERP login/register payloads, ERP-scoped storage keys, and backend error preservation**

```js
assert.equal(sentLoginPayload.domain, 'ERP')
assert.equal(sentRegisterPayload.domain, 'ERP')
assert.equal(localStorage.getItem('erp_token'), 'erp-token')
assert.equal(JSON.parse(localStorage.getItem('erp_userInfo')).accountDomain, 'ERP')
assert.equal(store.errorMessage, '该账号仅允许登录财务系统')
```

- [ ] **Step 2: Run the ERP auth store test to verify it fails**

Run: `node ./scripts/erp-auth-store.test.mjs`
Expected: FAIL until ERP-specific auth store behavior exists.

- [ ] **Step 3: Add ERP-specific login/register actions or a domain-configurable auth-store path without breaking finance storage isolation**

- [ ] **Step 3A: Wire `ErpLoginView.vue` and the shared auth forms so both ERP login and ERP registration submit through the ERP-scoped store path**

- [ ] **Step 3B: Ensure ERP page renders backend login/register errors directly from shared form or page state**

- [ ] **Step 4: Re-run the ERP auth store test and frontend build to verify they pass**

Run: `node ./scripts/erp-auth-store.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/src/stores/userStore.js lab-erp-demo/src/views/ErpLoginView.vue lab-erp-demo/src/components/auth/AuthCredentialsForm.vue lab-erp-demo/src/components/auth/AuthRegistrationForm.vue lab-erp-demo/scripts/erp-auth-store.test.mjs
git commit -m "feat: support erp scoped auth storage"
```

### Task 6: Add ERP-entry redirects and authenticated-session behavior

**Files:**
- Modify: `lab-erp-demo/src/router/index.js`
- Modify: `lab-erp-demo/src/router/domainAccess.js`
- Create: `lab-erp-demo/scripts/erp-auth-route.test.mjs`

- [ ] **Step 1: Write the failing route test for `/erp-login` authenticated redirect behavior and ERP post-login landing rule**

```js
assert.equal(getEntryRedirect({ accountDomain: 'FINANCE' }, '/erp-login'), '/finance/overview')
assert.equal(getErpLandingRoute({ role: 'BUSINESS' }), '/manager/dashboard')
assert.equal(getErpLandingRoute({ role: 'USER' }), '/workspace')
```

- [ ] **Step 2: Run the ERP auth route test to verify it fails**

Run: `node ./scripts/erp-auth-route.test.mjs`
Expected: FAIL until ERP entry redirects and landing rules are encoded.

- [ ] **Step 3: Add shared ERP entry redirect helpers and apply them in the router guard**

- [ ] **Step 4: Re-run the ERP auth route test and frontend build to verify they pass**

Run: `node ./scripts/erp-auth-route.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/src/router/index.js lab-erp-demo/src/router/domainAccess.js lab-erp-demo/scripts/erp-auth-route.test.mjs
git commit -m "feat: add erp auth entry redirects"
```

### Task 7: Final verification of ERP auth entry without breaking finance isolation

**Files:**
- Modify: `docs/superpowers/plans/2026-03-13-erp-auth-entry-plan.md`

- [ ] **Step 1: Run backend ERP auth-entry verification tests**

Run: `mvn -Dtest=AuthServiceTest,AuthControllerTest test`
Expected: PASS

- [ ] **Step 2: Run frontend ERP/finance auth entry verification and build**

Run: `node ./scripts/finance-login-view.test.mjs && node ./scripts/erp-login-view.test.mjs && node ./scripts/finance-auth-store.test.mjs && node ./scripts/erp-auth-store.test.mjs && node ./scripts/finance-route-domain-guard.test.mjs && node ./scripts/erp-auth-route.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 3: Verify the concrete behavior checklist**

```text
/login stays finance-only
/erp-login exists and is ERP-only
both pages use flip-card login/register interaction
finance auth submits FINANCE domain and uses finance storage keys
erp auth submits ERP domain and uses erp storage keys
finance login lands on /finance/overview
erp BUSINESS login lands on /manager/dashboard
erp non-BUSINESS login lands on /workspace
finance and erp auth isolation both remain intact
```

- [ ] **Step 4: Record implementation evidence in `docs/superpowers/plans/2026-03-13-erp-auth-entry-plan.md` during execution**

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-03-13-erp-auth-entry-plan.md
git commit -m "docs: track erp auth entry verification"
```

## Execution Evidence

- Backend verification passed:
  - `mvn -Dtest=AuthServiceTest,AuthControllerTest test`
- Frontend verification passed:
  - `node ./scripts/finance-login-view.test.mjs`
  - `node ./scripts/erp-login-view.test.mjs`
  - `node ./scripts/finance-auth-store.test.mjs`
  - `node ./scripts/erp-auth-store.test.mjs`
  - `node ./scripts/finance-route-domain-guard.test.mjs`
  - `node ./scripts/erp-auth-route.test.mjs`
  - `npm run build`

## Execution Notes

- ERP registration now submits the backend-required field set: `role`, `username`, `name`, `email`, `password`, `domain=ERP`.
- ERP login now redirects by role:
  - `BUSINESS` -> `/manager/dashboard`
  - other ERP roles -> `/workspace`
- Finance and ERP auth flows remain storage-isolated and route-isolated.
