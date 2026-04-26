# Finance Auth Isolation Implementation Plan

> Historical note (2026-03-15): this plan has been implemented. Finance and ERP auth are domain-aware, use separate login entries, and rely on backend-enforced account-domain validation. The repo is now in maintenance/update mode; keep this file as implementation history only.

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce a finance-only account domain so finance users log in through a Chinese finance-specific flow, are routed only into `/finance/*`, and cannot access ERP surfaces with finance-created accounts.

**Architecture:** Keep the existing auth endpoint family, but add an authoritative `accountDomain` attribute to backend user identity and enforce domain-aware login and route access in both `erp-backend` and `lab-erp-demo`. The frontend becomes finance-specific at the login/register surface, while backend auth and authorization remain the source of truth for account-domain isolation.

**Tech Stack:** Spring Boot 3.2, Spring Security, Spring MVC, Spring Data JPA, JUnit; Vue 3, Pinia, Vue Router, Element Plus, Vite

---

## File Structure Map

### Backend existing anchors in `erp-backend`

- Modify: `erp-backend/src/main/java/com/smartlab/erp/entity/User.java` - persist `accountDomain` on user identity
- Modify: `erp-backend/src/main/java/com/smartlab/erp/dto/LoginRequest.java` - accept requested login domain
- Modify: `erp-backend/src/main/java/com/smartlab/erp/dto/RegisterRequest.java` - accept finance-domain register semantics
- Modify: `erp-backend/src/main/java/com/smartlab/erp/dto/LoginResponse.java` if already used for richer auth payloads
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java` - register/login/domain enforcement and `/me` payload source
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/AuthController.java` - shared auth endpoint contract remains stable while carrying domain data
- Modify: `erp-backend/src/main/java/com/smartlab/erp/config/JwtUtil.java` - include `accountDomain` claim if current auth design depends on token claims for guards
- Modify: `erp-backend/src/main/java/com/smartlab/erp/security/JwtAuthenticationFilter.java` - propagate domain into authenticated principal context
- Modify: `erp-backend/src/main/java/com/smartlab/erp/security/UserPrincipal.java` - expose `accountDomain`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/config/SecurityConfig.java` - add finance-vs-ERP access boundaries

### Backend likely additions in `erp-backend`

- Create: `erp-backend/src/main/java/com/smartlab/erp/enums/AccountDomain.java`
- Create: `erp-backend/src/main/java/com/smartlab/erp/config/AccountDomainDataInitializer.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/controller/AuthControllerTest.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/security/DomainAccessIntegrationTest.java`

### Frontend existing anchors in `lab-erp-demo`

- Modify: `lab-erp-demo/src/views/LoginView.vue` - finance-only Chinese login/register UX
- Modify: `lab-erp-demo/src/stores/userStore.js` - send/consume `domain` and `accountDomain`
- Modify: `lab-erp-demo/src/router/index.js` - account-domain route guards for finance vs ERP surfaces
- Modify: `lab-erp-demo/src/router/financeRoutes.js` - keep finance route boundary explicit for guards

### Frontend likely additions in `lab-erp-demo`

- Create: `lab-erp-demo/src/router/domainAccess.js`
- Create: `lab-erp-demo/scripts/finance-auth-store.test.mjs`
- Create: `lab-erp-demo/scripts/finance-route-domain-guard.test.mjs`

### Existing backend protection anchors

- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/ProjectController.java` if present for ERP project APIs
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/UserController.java` if ERP-side profile/user APIs remain accessible from shared UI routes
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/AuthController.java` for `/api/auth/me` and auth contract shape

## Chunk 1: Backend account-domain enforcement in `erp-backend`

### Task 0: Backfill `accountDomain` for existing users before enforcement

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/config/AccountDomainDataInitializer.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/entity/User.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java`

- [ ] **Step 1: Write the failing test for existing users without `accountDomain` defaulting safely to `ERP` during rollout**

```java
@Test
void existingUserWithoutDomain_isBackfilledToErpByDefault() {
    User legacyUser = userRepository.save(User.builder().username("legacy").password("x").role("BUSINESS").build());
    initializer.backfillMissingDomains();
    assertThat(userRepository.findById(legacyUser.getUserId()).orElseThrow().getAccountDomain()).isEqualTo("ERP");
}
```

- [ ] **Step 2: Run the auth service test to verify it fails**

Run: `mvn -Dtest=AuthServiceTest test`
Expected: FAIL until legacy users receive a deterministic domain.

- [ ] **Step 3: Implement the smallest backfill initializer that sets missing domains to `ERP` by default and allows explicit finance pilot/test-account assignment through configuration or a focused allowlist hook**

- [ ] **Step 4: Re-run the auth service test to verify it passes**

Run: `mvn -Dtest=AuthServiceTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/config/AccountDomainDataInitializer.java erp-backend/src/main/java/com/smartlab/erp/entity/User.java erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java
git commit -m "feat: backfill account domains for existing users"
```

### Task 1: Persist `accountDomain` on user identity and auth DTOs

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/enums/AccountDomain.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/entity/User.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/dto/LoginRequest.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/dto/RegisterRequest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java`

- [ ] **Step 1: Write the failing backend test for default finance registration domain and login-request domain binding**

```java
@Test
void register_financeRequestPersistsFinanceDomain() {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("finance-user");
    request.setPassword("secret123");
    request.setName("Finance User");
    request.setEmail("finance@example.com");
    request.setRole("BUSINESS");
    request.setDomain("FINANCE");

    authService.register(request);

    User saved = userRepository.findByUsername("finance-user").orElseThrow();
    assertThat(saved.getAccountDomain()).isEqualTo("FINANCE");
}
```

- [ ] **Step 2: Run the backend auth service test to verify it fails**

Run: `mvn -Dtest=AuthServiceTest test`
Expected: FAIL until `accountDomain` exists and request DTOs bind the new field.

- [ ] **Step 3: Add the smallest possible `AccountDomain` enum and `User.accountDomain` persistence field**

```java
public enum AccountDomain {
    FINANCE,
    ERP
}
```

- [ ] **Step 4: Extend `LoginRequest` and `RegisterRequest` with explicit `domain` binding and validation**

```java
@NotBlank
private String domain;
```

- [ ] **Step 5: Re-run the auth service test to verify it passes**

Run: `mvn -Dtest=AuthServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/enums/AccountDomain.java erp-backend/src/main/java/com/smartlab/erp/entity/User.java erp-backend/src/main/java/com/smartlab/erp/dto/LoginRequest.java erp-backend/src/main/java/com/smartlab/erp/dto/RegisterRequest.java erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java
git commit -m "feat: add account domain to auth identity"
```

### Task 2: Enforce finance-domain register and login behavior in `AuthService`

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/AuthController.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/controller/AuthControllerTest.java`

- [ ] **Step 1: Write failing tests for domain-mismatch login and finance-only registration**

```java
@Test
void login_rejectsMissingDomainField() {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"finance-user","password":"secret123"}
            """))
        .andExpect(status().isBadRequest());
}

@Test
void login_allowsFinanceAccountWhenRequestedDomainIsFinance() { }

@Test
void login_rejectsFinanceAccountWhenRequestedDomainIsErp() { }

@Test
void login_rejectsErpAccountWhenRequestedDomainIsFinance() { }
```

- [ ] **Step 2: Run auth service and controller tests to verify they fail**

Run: `mvn -Dtest=AuthServiceTest,AuthControllerTest test`
Expected: FAIL until domain checks are enforced.

- [ ] **Step 3: Implement exact first-pass behavior in `AuthService`**

```java
String requestedDomain = request.getDomain().trim().toUpperCase(Locale.ROOT);
if (!requestedDomain.equals(user.getAccountDomain())) {
    throw new RuntimeException(requestedDomain.equals("FINANCE")
        ? "该账号不属于财务系统"
        : "该账号仅允许登录财务系统");
}
```

- [ ] **Step 4: Make finance registration persist `FINANCE` and reject non-finance domain values from the finance contract**

- [ ] **Step 5: Re-run auth service and controller tests to verify they pass**

Run: `mvn -Dtest=AuthServiceTest,AuthControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java erp-backend/src/main/java/com/smartlab/erp/controller/AuthController.java erp-backend/src/test/java/com/smartlab/erp/service/AuthServiceTest.java erp-backend/src/test/java/com/smartlab/erp/controller/AuthControllerTest.java
git commit -m "feat: enforce finance auth domain matching"
```

### Task 3: Return `accountDomain` through token/me identity and authenticated principal

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/config/JwtUtil.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/security/JwtAuthenticationFilter.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/security/UserPrincipal.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/controller/AuthControllerTest.java`

- [ ] **Step 1: Write the failing controller test asserting `/api/auth/me` includes `accountDomain`**

```java
mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer ..."))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.accountDomain").value("FINANCE"));
```

- [ ] **Step 2: Run the auth controller test to verify it fails**

Run: `mvn -Dtest=AuthControllerTest test`
Expected: FAIL until domain travels through auth identity.

- [ ] **Step 3: Add `accountDomain` to JWT claims and authenticated principal state**

```java
claims.put("accountDomain", accountDomain);
```

- [ ] **Step 4: Ensure `/api/auth/me` serializes or maps the domain field explicitly**

- [ ] **Step 5: Re-run the auth controller test to verify it passes**

Run: `mvn -Dtest=AuthControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/config/JwtUtil.java erp-backend/src/main/java/com/smartlab/erp/security/JwtAuthenticationFilter.java erp-backend/src/main/java/com/smartlab/erp/security/UserPrincipal.java erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java erp-backend/src/test/java/com/smartlab/erp/controller/AuthControllerTest.java
git commit -m "feat: expose auth account domain to clients"
```

### Task 4: Protect finance vs ERP backend surfaces by account domain

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/config/SecurityConfig.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/ProjectController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/UserController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceReportingController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAdjustmentController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceDividendController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceWorkbenchController.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAiController.java`
- Test: `erp-backend/src/test/java/com/smartlab/erp/security/DomainAccessIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests for finance-domain tokens hitting ERP routes and ERP-domain tokens hitting finance routes**

```java
@Test
void financeDomainToken_cannotReachManagerDashboardApis() {
    mockMvc.perform(get("/api/projects").header("Authorization", financeToken))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run the domain access integration test to verify it fails**

Run: `mvn -Dtest=DomainAccessIntegrationTest test`
Expected: FAIL until domain-aware access boundaries are enforced.

- [ ] **Step 3: Implement domain-aware access checks in centralized auth/security logic where possible**

- [ ] **Step 4: Keep the backend finance surface explicit as `/api/finance/**`, `/api/adjustment/**`, `/api/batch/**`, `/api/clearing/**`, `/api/dividend/**`, `/api/ai/**`, and `/api/rag/**`**

- [ ] **Step 5: Re-run the domain access test to verify it passes**

Run: `mvn -Dtest=DomainAccessIntegrationTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/config/SecurityConfig.java erp-backend/src/main/java/com/smartlab/erp/controller/ProjectController.java erp-backend/src/main/java/com/smartlab/erp/controller/UserController.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceReportingController.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAdjustmentController.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceDividendController.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceWorkbenchController.java erp-backend/src/main/java/com/smartlab/erp/finance/controller/FinanceAiController.java erp-backend/src/test/java/com/smartlab/erp/security/DomainAccessIntegrationTest.java
git commit -m "feat: isolate finance and erp backend surfaces"
```

## Chunk 2: Finance-only frontend login and routing in `lab-erp-demo`

### Task 5: Make the login/register UX finance-only and Chinese

**Files:**
- Modify: `lab-erp-demo/src/views/LoginView.vue`
- Create: `lab-erp-demo/scripts/finance-login-view.test.mjs`

- [ ] **Step 1: Write the failing node-based view test for finance-only Chinese login copy and missing ERP toggle**

```js
assert.match(source, /财务系统登录/)
assert.match(source, /财务系统注册/)
assert.match(source, /创建财务系统账号/)
assert.match(source, /注册财务账号/)
assert.doesNotMatch(source, /新 Vue 系统入口|旧 Python 前端入口|entry-switcher/)
```

- [ ] **Step 2: Run the login-view test to verify it fails**

Run: `node ./scripts/finance-login-view.test.mjs`
Expected: FAIL until the finance login page text and structure are isolated.

- [ ] **Step 3: Remove the finance/ERP entry switch and make the screen finance-specific in Chinese**

- [ ] **Step 4: Render backend domain-mismatch errors directly in `LoginView.vue` instead of collapsing them into a generic login failure**

- [ ] **Step 5: Make login success always route finance users to `/finance/overview`**

- [ ] **Step 6: Re-run the login-view test and frontend build to verify they pass**

Run: `node ./scripts/finance-login-view.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add lab-erp-demo/src/views/LoginView.vue lab-erp-demo/scripts/finance-login-view.test.mjs
git commit -m "feat: make login page finance specific"
```

### Task 6: Send and store finance-domain auth semantics in `userStore`

**Files:**
- Modify: `lab-erp-demo/src/stores/userStore.js`
- Create: `lab-erp-demo/scripts/finance-auth-store.test.mjs`

- [ ] **Step 1: Write the failing node-based store test for `domain=FINANCE` on login/register and `accountDomain` persistence**

```js
assert.equal(sentLoginPayload.domain, 'FINANCE')
assert.equal(sentRegisterPayload.domain, 'FINANCE')
assert.equal(store.userInfo.accountDomain, 'FINANCE')
assert.equal(store.errorMessage, '该账号仅允许登录财务系统')
```

- [ ] **Step 2: Run the store test to verify it fails**

Run: `node ./scripts/finance-auth-store.test.mjs`
Expected: FAIL until the store sends and persists domain semantics.

- [ ] **Step 3: Update `userStore.login()` and `userStore.register()` to send `domain: 'FINANCE'` from the finance flow**

- [ ] **Step 4: Persist returned `accountDomain` from `/api/auth/me` for route guards**

- [ ] **Step 4A: Surface backend domain-mismatch errors in store state without collapsing them into a generic login failure**

- [ ] **Step 5: Re-run the store test and build to verify they pass**

Run: `node ./scripts/finance-auth-store.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add lab-erp-demo/src/stores/userStore.js lab-erp-demo/scripts/finance-auth-store.test.mjs
git commit -m "feat: send finance auth domain from user store"
```

### Task 7: Enforce finance-vs-ERP route separation in the frontend router

**Files:**
- Modify: `lab-erp-demo/src/router/index.js`
- Modify: `lab-erp-demo/src/router/financeRoutes.js`
- Create: `lab-erp-demo/src/router/domainAccess.js`
- Create: `lab-erp-demo/scripts/finance-route-domain-guard.test.mjs`

- [ ] **Step 1: Write the failing route-guard test for finance users blocked from ERP routes and ERP users blocked from finance routes**

```js
assert.equal(resolveBlockedRoute({ accountDomain: 'FINANCE' }, '/manager/dashboard'), '/finance/overview')
assert.equal(resolveBlockedRoute({ accountDomain: 'FINANCE' }, '/workspace/project/1'), '/finance/overview')
assert.equal(resolveBlockedRoute({ accountDomain: 'FINANCE' }, '/profile'), '/finance/overview')
assert.equal(resolveBlockedRoute({ accountDomain: 'ERP' }, '/finance/overview'), '/manager/dashboard')
```

- [ ] **Step 2: Run the route-guard test to verify it fails**

Run: `node ./scripts/finance-route-domain-guard.test.mjs`
Expected: FAIL until domain-aware routing is implemented.

- [ ] **Step 3: Add a small shared route-domain resolver in `src/router/domainAccess.js` and apply it in `router.beforeEach`**

- [ ] **Step 4: Keep the finance route prefix boundary explicit and avoid duplicating domain logic in multiple route records**

- [ ] **Step 5: Re-run the route-guard test and frontend build to verify they pass**

Run: `node ./scripts/finance-route-domain-guard.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add lab-erp-demo/src/router/index.js lab-erp-demo/src/router/financeRoutes.js lab-erp-demo/src/router/domainAccess.js lab-erp-demo/scripts/finance-route-domain-guard.test.mjs
git commit -m "feat: isolate finance and erp frontend routes"
```

### Task 8: Final verification of finance auth isolation

**Files:**
- Modify: `docs/superpowers/plans/2026-03-12-finance-auth-isolation-plan.md`

- [ ] **Step 1: Run backend auth/domain verification tests**

Run: `mvn -Dtest=AuthServiceTest,AuthControllerTest,DomainAccessIntegrationTest test`
Expected: PASS

- [ ] **Step 2: Run frontend auth/domain verification and build**

Run: `node ./scripts/finance-login-view.test.mjs && node ./scripts/finance-auth-store.test.mjs && node ./scripts/finance-route-domain-guard.test.mjs && npm run build`
Expected: PASS

- [ ] **Step 3: Verify the concrete behavior checklist**

```text
Finance login page shows Chinese finance-specific labels only
Finance login submits domain=FINANCE
Finance register submits domain=FINANCE
Finance login redirects to /finance/overview
Finance account cannot enter /manager/* or /workspace/*
Finance account cannot enter /profile
ERP account cannot enter /finance/*
Auth /me returns accountDomain
Domain mismatch returns clear business error message
```

- [ ] **Step 4: Record migration notes and enforcement evidence in `docs/superpowers/plans/2026-03-12-finance-auth-isolation-plan.md` during execution**

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-03-12-finance-auth-isolation-plan.md
git commit -m "docs: track finance auth isolation verification"
```
