# ERP Auth Entry Design

> Historical note (2026-03-15): this design has been implemented. ERP now has a dedicated `/erp-login` page with shared `/api/auth/*` endpoints, ERP-scoped login/register flow, and frontend route/domain isolation. Keep this file as implementation background, but use `workflow.md`, deployment docs, and the running code as the current source of truth.

## Context

- `lab-erp-demo` uses `/login` for finance auth and `/erp-login` for ERP auth.
- Finance and ERP account domains are isolated through backend and frontend enforcement.
- This file records the design intent that led to the current ERP auth entry.

## Goal

Add a dedicated ERP authentication page that supports both login and registration on one screen through a flip-card interaction, while preserving the existing finance-vs-ERP account-domain isolation.

## Chosen Approach

Use two separate frontend auth pages with shared backend auth endpoints.

- Keep `/login` as the finance-specific auth page.
- Add a new ERP-specific auth page, recommended at `/erp-login`.
- Keep shared backend endpoints under `/api/auth/*`.
- Distinguish the two systems by explicit domain submission and backend domain enforcement.

## Why This Approach

- It preserves the finance-system isolation that was just completed.
- It gives ERP a clean and explicit entry point instead of reintroducing mixed-entry confusion.
- It avoids the larger rewrite cost of duplicating the backend authentication API family.

## Scope

### In Scope

- New ERP login/register page in `lab-erp-demo`
- Flip-card login/register interaction for the ERP page
- ERP-specific login/register request semantics (`domain=ERP`)
- ERP-specific local auth persistence on the frontend
- Restoring backend support for ERP registration while keeping finance registration intact
- Router entry and route-guard support for `/erp-login`

### Out of Scope

- Replacing the finance login page
- General auth redesign or SSO
- Full visual redesign of the existing finance page beyond what is needed for shared structure reuse

## Frontend Design in `lab-erp-demo`

### Route Structure

- `/login` remains the finance login/register page
- `/erp-login` becomes the ERP login/register page

### Interaction Model

The ERP page must support:

- front face: login
- back face: register
- click or CTA-driven flip-card transition between the two

The user explicitly requires login and registration to stay on one page rather than separate routes.

### Page Behavior

ERP login/register page should:

- use ERP-specific title, subtitle, and submit copy
- submit `domain=ERP`
- route successful login by one explicit rule:
  - `BUSINESS` -> `/manager/dashboard`
  - other ERP roles -> `/workspace`
- render backend errors directly, including domain mismatch errors

Authenticated-session behavior:

- if a FINANCE user opens `/erp-login`, frontend should redirect to `/finance/overview`
- if an ERP user opens `/erp-login` while already authenticated, frontend should redirect by the same ERP post-login rule

### Recommended Frontend Decomposition

Do not duplicate the full finance login view blindly.

Recommended split:

- extract shared auth-card shell / shared auth-form behaviors where possible
- keep finance and ERP copy/domain/redirect behavior in small per-page config or page-level composition

This keeps the flip-card behavior consistent while preventing two large pages from drifting apart.

### Local Storage Isolation

ERP login state must not reuse finance local-storage keys.

Recommended ERP keys:

- `erp_token`
- `erp_userInfo`

Finance keys remain separate.

## Backend Design in `erp-backend`

### Shared Auth Endpoint Family

Keep the shared endpoints:

- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET /api/auth/me`

### ERP Registration Behavior

The backend must restore support for ERP registration.

Required behavior:

- ERP page registration writes `accountDomain=ERP`
- finance page registration writes `accountDomain=FINANCE`
- backend must validate that incoming domain is one of the supported domains

First-pass ERP registration contract:

- ERP registration reuses the same base field set as the finance register form:
  - `role`
  - `username`
  - `name`
  - `email`
  - `password`
- ERP registration continues to use the existing backend-accepted role set already supported by `AuthService`
- This change does not introduce a new ERP-only role-validation system; it only restores `domain=ERP` registration through the shared auth endpoints

Because the frontend now has two valid registration sources, the backend can no longer force every register request to `FINANCE`.

### Login Behavior

Existing domain-aware login rules remain:

- ERP account can log into ERP page only
- finance account can log into finance page only
- wrong-domain login returns a clear business error

## Data Flow

### Finance Auth Flow

- `/login`
- login/register submit `domain=FINANCE`
- token/profile persisted under finance-scoped storage keys
- success route -> `/finance/overview`

### ERP Auth Flow

- `/erp-login`
- login/register submit `domain=ERP`
- token/profile persisted under ERP-scoped storage keys
- success route -> `/manager/dashboard` for `BUSINESS`, otherwise `/workspace`

### Shared Identity Profile

`/api/auth/me` continues to return:

- user id
- role
- accountDomain

Frontend route guards use `accountDomain` to decide which route families are legal.

## Error Handling

Both auth pages should directly display backend auth-domain errors.

Examples:

- ERP account trying finance login -> `该账号不属于财务系统`
- finance account trying ERP login -> `该账号仅允许登录财务系统`

ERP registration validation errors should also remain transparent:

- username exists
- invalid role
- invalid email
- invalid domain

## Testing Strategy

### Backend Tests

Add tests for:

- ERP registration persists `ERP`
- finance registration still persists `FINANCE`
- ERP login to ERP succeeds
- ERP login to finance fails
- finance login to ERP fails

### Session and Storage Notes

- ERP page uses ERP-scoped storage keys for token and user info only in this change
- This spec does not require additional ERP-scoped auth persistence beyond token/profile storage

### Frontend Tests

Add tests for:

- `/erp-login` page exists and uses flip-card login/register interaction
- ERP login submits `domain=ERP`
- ERP registration submits `domain=ERP`
- ERP auth page uses ERP-scoped local-storage keys
- ERP login success redirects to ERP landing route
- finance and ERP auth pages do not reuse each other’s domain/configuration/state

## Acceptance Criteria

- ERP users have a dedicated login/register page.
- ERP login/register live on a single flip-card page.
- ERP page submits `domain=ERP`.
- ERP registration creates `ERP`-domain accounts.
- ERP login succeeds only for `ERP` accounts.
- Finance isolation remains intact.
- Finance and ERP local auth state remain separated.

## Next Step

Create an implementation plan that updates `lab-erp-demo` and `erp-backend` together for a dedicated ERP auth page, ERP-scoped registration/login behavior, and shared auth reuse without breaking finance isolation.
