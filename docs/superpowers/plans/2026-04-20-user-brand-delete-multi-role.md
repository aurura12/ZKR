# User Brand Rename, User Deletion, and Multi-Role Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the visible product brand to `实验室`, remove the `瀧月` user record safely, and support multi-role membership without changing login authentication.

**Architecture:** Keep `sys_user.role` as the primary login role and reuse the existing `sys_user_role` table for extra role matches. Perform the user removal as a one-time backend cleanup path that deletes direct per-user rows first, then deletes the primary user row. Update the frontend brand text only where the product name is visible.

**Tech Stack:** Vue 3, Pinia, Spring Boot, Spring Data JPA, PostgreSQL, Docker Compose

---

### Task 1: Rename visible brand text to `实验室`

**Files:**
- Modify: `lab-erp-demo/index.html`
- Modify: `lab-erp-demo/src/App.vue`
- Modify: `lab-erp-demo/src/views/LoginView.vue`
- Modify: `lab-erp-demo/src/views/ErpLoginView.vue`

- [ ] **Step 1: Write the failing test**

No new automated test is required for this text-only rename. Verify by searching the source for the old brand text before editing.

- [ ] **Step 2: Run check to verify current state**

Run: `grep -R "智能博弈实验室" lab-erp-demo/index.html lab-erp-demo/src/App.vue lab-erp-demo/src/views/LoginView.vue lab-erp-demo/src/views/ErpLoginView.vue`

Expected: matches are present before the edit.

- [ ] **Step 3: Write minimal implementation**

Replace the visible brand text with `实验室` in the files listed above.

- [ ] **Step 4: Run check to verify it passes**

Run: `grep -R "智能博弈实验室" lab-erp-demo/index.html lab-erp-demo/src/App.vue lab-erp-demo/src/views/LoginView.vue lab-erp-demo/src/views/ErpLoginView.vue`

Expected: no matches.

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/index.html lab-erp-demo/src/App.vue lab-erp-demo/src/views/LoginView.vue lab-erp-demo/src/views/ErpLoginView.vue
git commit -m "chore: rename visible brand text"
```

### Task 2: Delete the `瀧月` user safely

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/config/UserCleanupRunner.java`
- Create: `erp-backend/src/test/java/com/smartlab/erp/config/UserCleanupRunnerTest.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/repository/UserBadgeRepository.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/repository/UserRoleRepository.java`

- [ ] **Step 1: Write the failing test**

Create a focused test that stubs a single user with `name = "瀧月"`, one `user_badge` row, and one `sys_user_role` row, then asserts the cleanup runner deletes the direct rows and the user row.

```java
@Test
void deletesSingleUserNamedLuoyueAndDirectPerUserRows() {
    User target = User.builder().userId("U-999").name("瀧月").username("luoyue").build();
    when(userRepository.findAll()).thenReturn(List.of(target));
    when(userBadgeRepository.findByUserIdOrderByCreatedAtDesc("U-999")).thenReturn(List.of(new UserBadge()));
    when(userRoleRepository.findByUserId("U-999")).thenReturn(List.of(new UserRole()));

    cleanupRunner.run(new DefaultApplicationArguments(new String[0]));

    verify(userBadgeRepository).deleteByUserId("U-999");
    verify(userRoleRepository).deleteByUserId("U-999");
    verify(userRepository).delete(target);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=UserCleanupRunnerTest test`

Expected: FAIL because `UserCleanupRunner` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Implement `UserCleanupRunner` as an `ApplicationRunner` with a small, explicit deletion flow:

```java
@Configuration
@RequiredArgsConstructor
public class UserCleanupRunner {
    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserRoleRepository userRoleRepository;

    @Bean
    @Order(111)
    ApplicationRunner removeLuoyueUser() {
        return args -> {
            List<User> matches = userRepository.findAll().stream()
                .filter(user -> "瀧月".equals(user.getName()))
                .toList();

            if (matches.size() != 1) {
                return;
            }

            User target = matches.get(0);
            userBadgeRepository.deleteByUserId(target.getUserId());
            userRoleRepository.deleteByUserId(target.getUserId());
            userRepository.delete(target);
        };
    }
}
```

Add repository delete methods if they do not already exist:

```java
void deleteByUserId(String userId);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=UserCleanupRunnerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/config/UserCleanupRunner.java erp-backend/src/test/java/com/smartlab/erp/config/UserCleanupRunnerTest.java erp-backend/src/main/java/com/smartlab/erp/repository/UserBadgeRepository.java erp-backend/src/main/java/com/smartlab/erp/repository/UserRoleRepository.java
git commit -m "fix: clean up named user safely"
```

### Task 3: Allow a user to have multiple roles without affecting authentication

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/service/UserRoleMatchService.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/WorkflowMemberRoleService.java` if business role lookup should include secondary roles
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/ProjectService.java` only if business filtering needs combined role matching
- Create: `erp-backend/src/test/java/com/smartlab/erp/service/UserRoleMatchTest.java`

- [ ] **Step 1: Write the failing test**

Add a test that proves role matching can see both the primary role and the extra `sys_user_role` role, while `UserDetails` authority resolution still comes from `sys_user.role`.

```java
@Test
void userCanMatchPrimaryAndSecondaryRolesWithoutChangingAuthorities() {
    User user = User.builder().userId("U-1").role("DEV").build();
    UserRole extra = UserRole.builder().userId("U-1").role("DATA").build();
    when(userRoleRepository.findByUserId("U-1")).thenReturn(List.of(extra));

    assertThat(roleMatchService.matches(user, "DEV")).isTrue();
    assertThat(roleMatchService.matches(user, "DATA")).isTrue();
    assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_DEV");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=UserRoleMatchTest test`

Expected: FAIL because the combined role matcher does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Add a tiny service method or helper that checks:

```java
boolean matches(User user, String role) {
    if (user != null && role != null && role.equalsIgnoreCase(user.getRole())) {
        return true;
    }
    return userRoleRepository.findByUserId(user.getUserId()).stream()
        .anyMatch(userRole -> role.equalsIgnoreCase(userRole.getRole()));
}
```

Do not change `UserDetails#getAuthorities()` or login/auth token creation. Keep those flows on `sys_user.role` only.

- [ ] **Step 4: Run test to verify it passes**

Run: `docker run --rm -v /home/a/zhangqi/workspace/ZKR/erp-backend:/app -w /app maven:3.9.9-eclipse-temurin-17 mvn -q -Dtest=UserRoleMatchTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-backend/src/main/java/com/smartlab/erp/service/RoleMatchService.java erp-backend/src/test/java/com/smartlab/erp/service/UserRoleMatchTest.java
git commit -m "feat: support additive user role matches"
```

### Task 4: Rebuild and verify the running containers

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Update image tags if needed**

If the backend or frontend version tags changed during implementation, update the relevant `image:` lines in `docker-compose.yml` to the exact new tags produced by the builds.

- [ ] **Step 2: Rebuild images**

Run the same `docker build` / `docker push` commands that this repo already uses for backend and frontend release images, substituting the new tag numbers from the build output. Repeat for the frontend only if the brand rename changes the frontend image tag.

- [ ] **Step 3: Recreate containers**

Run:

```bash
docker compose up -d erp-backend
docker compose up -d lab-erp-demo
```

- [ ] **Step 4: Verify runtime behavior**

Run:

```bash
curl -I http://127.0.0.1:8080/login
curl -I http://127.0.0.1:8080/erp-login
docker logs --tail 80 zkr-erp-backend
```

Expected: login pages return 200, and backend logs do not show a startup crash.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml
git commit -m "chore: refresh runtime image references"
```
