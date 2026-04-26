# Finance Auth Isolation Design

> Historical note (2026-03-15): this design is implemented. Finance and ERP now use isolated entry routes (`/login` and `/erp-login`), backend account-domain enforcement, domain-aware `/api/auth/login`, and route/domain guards on the frontend. Keep this file as design background; use `workflow.md`, deployment docs, and the current codebase for operational truth.

## Context

- The finance and ERP systems now use separate login entries and domain-aware auth.
- Shared auth endpoints remain in place, but account-domain isolation is enforced in backend and frontend runtime logic.
- The user explicitly requires strong separation:
  - the finance system UI should be Chinese
  - finance login should not allow perspective switching into ERP
  - accounts registered from the finance system must not be able to log into ERP

## Goal

Introduce a strong finance-only account domain so finance users authenticate and operate only inside the finance console, while ERP users remain isolated from the finance console unless explicitly allowed later.

## Chosen Approach

Use a shared authentication API surface with a new authoritative account-domain field.

- Keep the existing auth endpoint family to reduce migration cost.
- Add backend-enforced domain matching for login and route access.
- Make the finance frontend send and consume finance-domain semantics explicitly.

## Why This Approach

- It satisfies the user requirement for backend-enforced separation.
- It avoids the larger rewrite cost of introducing a second parallel auth subsystem.
- It keeps the implementation focused on account-domain isolation, not a general auth redesign.

## Scope

### In Scope

- Finance login and register UX in `lab-erp-demo`
- Finance-only routing behavior in `lab-erp-demo`
- Auth request and user-profile contract changes between frontend and backend
- Backend account-domain persistence and enforcement in `erp-backend`
- Blocking finance accounts from ERP entry points
- Blocking ERP accounts from finance entry points unless explicitly allowed later

### Out of Scope

- Full auth-system rewrite
- SSO or multi-tenant auth design
- Broad ERP login redesign unrelated to finance isolation
- Password recovery redesign

## Domain Model Change

Add an authoritative account-domain attribute to user identity.

Recommended values:

- `FINANCE`
- `ERP`

This attribute must be persisted in backend user data and returned by the authenticated user profile endpoint.

## Frontend Design in `lab-erp-demo`

### Login Page Behavior

- The finance-facing login page becomes finance-specific.
- Remove the ERP/finance entry toggle from the finance login UX.
- Replace migration-era wording such as "old system" or "new Vue system" with finance-specific Chinese wording.

Recommended wording direction:

- title: finance system login
- register title: finance system registration
- success destination description: enter finance console after login

Required first-pass Chinese labels/messages:

- login heading: `财务系统登录`
- login subtitle: `请输入财务账号信息`
- register heading: `财务系统注册`
- register subtitle: `创建财务系统账号`
- submit success destination hint: `登录后进入财务控制台`
- finance submit button: `登录`
- register button: `注册财务账号`

This change only requires finance-login wording to be Chinese and finance-specific. It does not require a full i18n framework rollout.

### Request Contract

Finance login request should explicitly include the target domain.

Example intent:

- `POST /api/auth/login` with `domain=FINANCE`

Finance registration request should also explicitly include or imply finance domain.

Example intent:

- `POST /api/auth/register` with `domain=FINANCE`

Required first-pass frontend contract:

- finance login always sends `domain: FINANCE`
- finance registration always sends `domain: FINANCE`
- finance frontend never renders or submits an ERP-target login switch in this flow

### Route Guard Behavior

- Finance-authenticated users may access only `/finance/*` routes.
- If a finance user tries to access `/manager/*`, `/workspace/*`, or other ERP routes, frontend guards redirect them back to `/finance/overview` or `/login`.
- If an ERP user tries to access `/finance/*`, frontend guards block access and redirect to the ERP home route or login.

Frontend guards are convenience and UX only; backend remains authoritative.

## Backend Design in `erp-backend`

### Login Enforcement

The backend login flow must validate:

1. username/password correctness
2. requested domain matches stored account domain

If the credentials are valid but the domain does not match, return a clear business error.

Example messages:

- finance account attempting ERP login: account is restricted to the finance system
- ERP account attempting finance login: account does not belong to the finance system

### Register Enforcement

- Finance registration creates users with account domain `FINANCE`.
- ERP registration, if still present, creates users with account domain `ERP`.
- The backend must not trust the frontend blindly if the endpoint is shared; it should validate that finance registration paths cannot create ERP-domain accounts from the finance UI contract.

Required first-pass backend contract behavior:

- if `POST /api/auth/register` is used by the finance frontend, the backend persists `accountDomain=FINANCE`
- if a shared register endpoint still accepts a domain field, values other than `FINANCE` must be rejected for the finance UI path
- if the backend keeps a separate ERP-side registration flow, that flow remains out of scope for this change unless needed to preserve current ERP behavior

### Authenticated User Profile

`/api/auth/me` must return account-domain information so the frontend can enforce domain-aware route guards.

Minimum relevant fields:

- user id
- role
- account domain

Suggested response field name:

- `accountDomain`

### ERP Route Protection

ERP-facing APIs and route-level authorization logic must reject finance-domain accounts even if they present a valid token.

First-pass ERP surface definition in this change:

- frontend routes: `/manager/*`, `/workspace/*`, `/profile`
- backend intent: non-finance workbench endpoints and controllers serving ERP project/workspace behavior

### Finance Route Protection

Finance-facing APIs should reject ERP-domain accounts unless future requirements explicitly define shared-admin exceptions.

First-pass finance surface definition in this change:

- frontend routes: `/finance/*`
- backend controllers/modules: finance controllers and finance service entry points under `com.smartlab.erp.finance`

Recommended enforcement placement:

- frontend: router guard by `accountDomain`
- backend: auth/authorization layer before controller business logic, with controller-level or centralized guard checks acceptable as long as they are consistent

## Migration Strategy

### Existing Accounts

Use a simple initial migration rule:

- newly registered finance accounts -> `FINANCE`
- existing ERP accounts -> `ERP`

Additional rollout rule for already-existing users who currently use the finance console:

- any known current finance pilot/test accounts must be explicitly migrated to `FINANCE` before enforcement is enabled
- accounts without explicit finance assignment should default to `ERP`, not dual-access
- do not infer dual access automatically from current role alone

Do not introduce a shared or hybrid domain in this change.

If future requirements need dual-access users, handle them in a separate design and implementation cycle.

Session rollout behavior:

- existing tokens may become invalid after domain enforcement starts
- users should be required to re-login after the migration if their session payload does not contain account-domain information

## Error Handling

Frontend should render backend domain errors directly instead of collapsing everything into a generic login failure.

Required behavior:

- invalid password remains a normal login failure
- valid credentials but wrong domain becomes a specific domain error
- unauthorized route access redirects predictably and may show a route-level notice if needed

Suggested domain-mismatch messages:

- finance account attempting ERP login: `该账号仅允许登录财务系统`
- ERP account attempting finance login: `该账号不属于财务系统`

Suggested first-pass contract table:

| Endpoint | Request | Required domain behavior | Key response fields |
|---|---|---|---|
| `/api/auth/login` | `username`, `password`, `domain` | credentials must match requested domain | `token`, `message` |
| `/api/auth/register` | registration fields + `domain=FINANCE` | finance UI can only create `FINANCE` accounts | success message |
| `/api/auth/me` | authenticated request | returns stored domain for current user | `id`, `role`, `accountDomain` |

## Chinese UI Requirement

The finance login entry must use Chinese finance-specific wording.

At minimum, remove mixed-system terminology from:

- finance login button labels
- entry descriptions
- finance registration labels where they imply general ERP registration

## Testing Strategy

### Backend Tests

Add tests for:

- finance account logs into finance successfully
- finance account login to ERP fails with domain error
- ERP account login to finance fails with domain error
- `/api/auth/me` includes account domain
- finance-domain token rejected from ERP-protected routes
- ERP-domain token rejected from finance-protected routes

### Frontend Tests

Add tests for:

- finance login submits `domain=FINANCE`
- finance registration submits `domain=FINANCE`
- login page no longer exposes ERP perspective toggle in finance mode
- finance user is redirected to `/finance/overview`
- finance user cannot navigate to ERP pages
- ERP user cannot navigate to finance pages

## Acceptance Criteria

- Finance login UI is Chinese and finance-specific.
- Finance users cannot switch into ERP from the finance login flow.
- Finance-registered accounts cannot log into ERP.
- Finance accounts cannot access ERP routes even by manually changing the URL.
- ERP accounts cannot access finance routes unless explicitly allowed in a future change.

## Non-Goals

- No dual-access admin model in this change.
- No shared "both systems" account state in this change.
- No general localization framework rollout.

## Next Step

Create an implementation plan that updates `lab-erp-demo` and `erp-backend` together for account-domain persistence, auth contract changes, finance-only route guarding, and Chinese finance login UX.
