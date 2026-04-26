# Finance Console Rebuild Design

> Historical note (2026-03-16): this is a development-phase design document, not the current operational state of the repo. The repo is now in maintenance/update mode. Use `workflow.md`, `docs/deployment/docker-mother-child.md`, and `docs/superpowers/current-project-memory.md` for current work.

## Context

- Source requirements come from `ambition.md`.
- Backend implementation must be written in `erp-backend`.
- Frontend implementation must be written in `lab-erp-demo`.
- The chosen execution strategy is backend-first: stabilize domain rules, services, DTOs, and APIs before concentrated frontend integration.

## Goal

Rebuild the ERP finance console as a finance-centered system with stable business rules, auditable financial mutations, and a phased delivery path from P0 to P2.

## Delivery Strategy

### Chosen Approach

Use a backend-first implementation strategy.

- First, complete finance domain modeling and API contracts in `erp-backend`.
- Second, keep the existing finance shell in `lab-erp-demo` as the integration surface.
- Third, connect frontend modules after backend contracts and transactional rules are stable.

### Why This Approach

- The requirements are rule-dense and transaction-heavy.
- Clearing, dividend confirmation, reconciliation, and royalty calculation must be authoritative on the backend.
- This minimizes frontend rework caused by unstable DTOs or business semantics.

## Scope Decomposition

### Phase 1 - P0 Finance Core

- Financial statements
- Manual adjustment and audit log
- Clearing execution
- Dividend prepare/list/confirm
- Wallet balances and wallet transactions
- Bank balance snapshot input and reconciliation output

### Phase 2 - P1 Cost and Venture Linkage

- Cost batch execution
- Venture cost preview
- Venture list and dashboard linkage
- Additional statement enrichments needed by overview dashboards

### Phase 3 - P2 AI and RAG

- Finance AI chat
- RAG query and push
- Finance context aggregation for AI responses
- Graceful downgrade from AI to RAG

## Architecture

### Backend Role

`erp-backend` is the source of truth for:

- business formulas
- state transitions
- transactional write flows
- audit semantics
- API response contracts

The backend should expose stable finance endpoints grouped by business capability, not by UI page alone.

### Frontend Role

`lab-erp-demo` is responsible for:

- console layout and navigation
- page composition
- form handling and confirmation UX
- status display, formatting, and empty/error states
- calling backend contracts without re-implementing finance formulas

The frontend may compute display-only values such as formatted percentages, money presentation, and status labels, but not settlement or dividend formulas.

## Module Boundaries

### Central Dashboard and Overview Module

Responsibility:

- Own the central dashboard page and its backend aggregation scope.
- Combine KPI cards, risk venture rows, cash-flow summary, and statement highlights.
- Read from statements, clearing results, wallet rollups, and high-risk venture projections.
- Support the overview page in `lab-erp-demo` as the first finance landing surface.

Primary contracts:

- `GET /api/finance/statements`
- `POST /api/rag/query` for risk-oriented intelligence fallback or auxiliary insight rendering

Acceptance focus:

- KPI cards render authoritative values from backend aggregates.
- Risk table loads only the required rows for the current view.
- Initial page load refreshes automatically without preloading unrelated business modules.

### Finance Statements Module

Responsibility:

- Aggregate clearing, wallet, cash flow, bank balance, and adjustment data.
- Serve `GET /api/finance/statements` as the unified reporting entry point.

Non-responsibility:

- Does not mutate financial ledgers or balances directly.

### Manual Adjustment Module

Responsibility:

- Validate subject, direction, amount, operator, and reference fields.
- Persist manual adjustment records.
- Feed reconciliation via adjustment net amount.

Non-responsibility:

- Does not directly mutate bank balance snapshots.

Primary contracts:

- `POST /api/adjustment/create`
- `GET /api/adjustment/list`

### Cost Batch Module

Responsibility:

- Read work-hour inputs and user base monthly cost.
- Apply the batch formulas.
- Persist normalized batch cost records.

Non-responsibility:

- Does not perform venture clearing.

### Clearing Module

Responsibility:

- Accept `venture_id` and `final_revenue`.
- Summarize leveraged cost.
- Calculate middleware royalty fees.
- Produce clearing result.
- Write royalty usage, wallet transactions, wallet balance updates, and cash flow entries.

This is a strong transactional module.

Primary contracts:

- `GET /api/clearing/ventures`
- `POST /api/clearing/execute`

### Dividend Module

Responsibility:

- Prepare pending dividend sheets for ventures with positive cleared profit.
- Prevent duplicate pending sheets for the same venture.
- Confirm dividends only after bank-balance assertion passes.
- Write wallet changes, wallet transactions, cash flow outflows, and new bank balance snapshots.

This is a strong transactional module.

### Wallet Module

Responsibility:

- Expose wallet balances and transaction audit data.
- Preserve traceability from every balance change back to business events.

Primary contracts:

- `GET /api/finance/wallets`
- `GET /api/finance/transactions`

List semantics:

- wallet list returns current balance, cumulative dividend, cumulative royalty, role, and updated time per user
- transaction list returns transaction type, amount, source table, source business id, remark, and created time

### Bank Balance and Reconciliation Module

Responsibility:

- Accept manual bank balance snapshots.
- Provide latest snapshot context to reconciliation and dividend confirmation.
- Expose the latest available bank snapshot through finance statement aggregation.

Primary contracts:

- `POST /api/finance/bank_balance`
- `GET /api/finance/statements` includes reconciliation output built from latest snapshot

Non-responsibility:

- Does not mutate wallet balances or dividend sheets by itself.

### AI and RAG Module

Responsibility:

- Build finance-scoped context from financial snapshots and recent business records.
- Call AI first, then downgrade to RAG if AI fails.

Non-responsibility:

- Must not write financial business records.

## Core Data Flow

Primary financial flow:

- cost batch -> clearing -> dividend -> wallet/transaction/cash flow

Reconciliation flow:

- adjustment -> theoretical balance calculation
- bank balance snapshot -> reconciliation result and dividend-confirm assertion

Intelligence flow:

- clearing + dividend + wallet + transactions + adjustments + cash flow + bank snapshots -> AI/RAG context

## Endpoint Ownership

| Endpoint | Backend owning module in `erp-backend` | Frontend surface in `lab-erp-demo` | Contract intent |
|---|---|---|---|
| `GET /api/finance/statements` | central dashboard and finance statements | `FinanceOverviewView.vue` | unified KPI, statement, cash-flow, and reconciliation aggregate |
| `POST /api/finance/bank_balance` | bank balance and reconciliation | finance overview/reporting form | create bank balance snapshot with operator and remark |
| `POST /api/adjustment/create` | manual adjustment | `AdjustmentCenterView.vue` | create adjustment record |
| `GET /api/adjustment/list` | manual adjustment | `AdjustmentCenterView.vue` | list adjustment audit rows |
| `POST /api/batch/run_cost` | cost batch | `CostBatchView.vue` | execute one ledger-month batch |
| `GET /api/batch/preview/{ventureId}` | cost batch | `CostBatchView.vue` | preview venture cost detail for a ledger month |
| `GET /api/clearing/ventures` | clearing | `ClearingCenterView.vue` | list ventures and current clearing readiness |
| `POST /api/clearing/execute` | clearing | `ClearingCenterView.vue` | execute clearing for one venture |
| `POST /api/dividend/prepare` | dividend | `DividendCenterView.vue` | generate pending dividend sheets |
| `GET /api/dividend/list` | dividend | `DividendCenterView.vue` | query dividend sheets by status |
| `POST /api/dividend/confirm` | dividend | `DividendCenterView.vue` | confirm pending dividend payout |
| `GET /api/finance/wallets` | wallet | `FinanceWalletsView.vue` | list wallet summary rows |
| `GET /api/finance/transactions` | wallet | `FinanceWalletsView.vue` | list wallet audit transactions |
| `POST /api/ai/chat` | AI and RAG | `FinanceAiChatView.vue` | primary finance AI conversation |
| `POST /api/rag/query` | AI and RAG | `RagSearchView.vue` and overview assistant widgets | finance semantic retrieval |
| `POST /api/rag/push` | AI and RAG | admin-triggered maintenance surface | rebuild finance retrieval context |

## Authorization Boundaries

Execution rights should be enforced in `erp-backend` and reflected in `lab-erp-demo` UI visibility.

- founder/super-admin and finance-admin may execute clearing, dividend prepare/confirm, bank snapshot entry, and manual adjustment creation
- business lead may read overview, statements, venture clearing lists, and dividend lists, but not execute high-risk finance writes
- middleware developer may read only their own relevant royalty and wallet-related data, not full statements by default
- normal viewer may access only restricted read-only summary data when explicitly enabled

The frontend should hide unavailable actions, but backend authorization remains authoritative.

## Business Rules Placement

All authoritative rules live in `erp-backend` services.

This includes:

- cost batch formulas
- clearing formulas
- royalty fee formulas
- dividend tail-difference balancing
- bank balance assertion before confirmation
- reconciliation formulas
- state transition rules

`lab-erp-demo` should only render outputs and drive user interaction around those outputs.

## Error Handling Design

All finance endpoints should use one consistent response envelope with at least:

- success indicator
- message
- data
- traceId

Business validation failures must be explicit and frontend-usable, including cases such as:

- missing ledger month
- invalid debit/credit direction
- amount less than or equal to zero
- venture not eligible for clearing
- duplicate pending dividend sheet
- insufficient bank balance for dividend confirmation

Operational and state errors must also be explicit, including:

- latest bank snapshot missing when confirmation or reconciliation depends on it
- venture, dividend sheet, wallet, or snapshot target not found
- permission denied for the current role
- duplicate submit after timeout or retry
- concurrent clearing or concurrent dividend confirmation against the same venture
- stale status transition where another request has already changed the target state

High-risk operation failures must produce messages suitable for confirmation dialogs and form-level errors.

## Transaction Boundaries

### Clearing Execute

Single transaction covering:

- clearing result write
- middleware usage write
- wallet transaction write
- wallet balance update
- cash flow write

All succeed together or roll back together.

Business uniqueness and idempotency rule:

- one effective clearing result per venture per settlement cycle
- repeated submit with the same venture and same settlement intent must return the already-finalized result or reject as already cleared

### Dividend Confirm

Single transaction covering:

- bank balance assertion
- dividend sheet confirmation
- wallet balance update
- dividend wallet transaction write
- cash flow outflow write
- new bank balance snapshot write

All succeed together or roll back together.

Business uniqueness and idempotency rule:

- one confirmation transition from `PENDING` to `CONFIRMED` per dividend sheet
- repeated confirm on an already confirmed sheet must not create duplicate wallet or cash-flow writes

### Adjustment Create

Single transaction covering only the adjustment record itself.

Reconciliation remains query-time aggregation, not a cascading write.

### Cost Batch Run

Single transaction for batch persistence under one ledger month, with repeat-execution protection.

Business uniqueness and idempotency rule:

- one active batch result set per `fin_ledger_month`
- repeated run for the same ledger month must either be rejected or routed through an explicit replace/rebuild path defined in implementation, but not silently duplicate rows

## Idempotency and Audit

The backend should add duplicate-execution protection for:

- cost batch run
- clearing execution
- dividend confirmation

The minimum dedupe keys are:

- cost batch: `fin_ledger_month`
- clearing: `venture_id` plus current settlement cycle identity
- dividend prepare: `venture_id` plus `PENDING` status uniqueness
- dividend confirm: dividend sheet id or venture-scoped pending-sheet set being confirmed

Audit coverage should include:

- operator identity when available
- operation timestamp
- target business object
- result summary

High-risk actions that require explicit confirmation in product behavior:

- dividend confirmation
- bank balance snapshot entry
- manual adjustment creation
- clearing execution

## Testing Strategy

### Backend First

Tests should start in `erp-backend`.

Priority coverage:

- service-level formula validation
- state and eligibility rules
- transaction success and rejection paths
- controller DTO validation
- unified response envelope behavior

Minimum P0 scenario coverage:

- clearing with positive profit
- clearing with negative profit transferred to company
- duplicate pending dividend prevention
- dividend confirmation rejected by insufficient bank balance
- manual adjustment direction validation
- reconciliation difference calculation

### Frontend After Contract Stabilization

Tests in `lab-erp-demo` should focus on:

- store behavior with real API contract shapes
- page loading states
- form validation and confirmation dialogs
- status/tag rendering
- error and empty states

## Frontend Integration Plan

The existing finance route shell in `lab-erp-demo` should remain the container for integration.

Frontend implementation order after backend stabilization:

1. Statements and overview
2. Wallets and transaction audit
3. Adjustments
4. Clearing
5. Dividends
6. Cost batch
7. AI and RAG

This order follows dependency depth and business risk.

## Phase Deliverables

### Phase 1 - P0

Backend deliverables in `erp-backend`:

- statement aggregation contract
- bank snapshot write contract
- adjustment create/list contract
- clearing venture list and execute contract
- dividend prepare/list/confirm contract
- wallet summary and transaction list contract
- service-level transactional rules for clearing and dividend confirm

Frontend deliverables in `lab-erp-demo`:

- finance overview page wired to statement aggregate and bank snapshot input
- wallet page wired to wallet and transaction APIs
- adjustment page wired to create/list APIs
- clearing page wired to venture list and execute APIs
- dividend page wired to prepare/list/confirm APIs

### Phase 2 - P1

Backend deliverables in `erp-backend`:

- cost batch execute and preview contracts
- venture readiness data for downstream overview and clearing linkage
- enriched overview aggregates needed for cross-module navigation

Frontend deliverables in `lab-erp-demo`:

- cost batch page with ledger-month execution and preview
- overview-to-clearing and overview-to-batch linkage polish
- stronger empty/error/result states for reporting workbench pages

### Phase 3 - P2

Backend deliverables in `erp-backend`:

- AI chat contract
- RAG query/push contracts
- finance context assembly service with read-only guarantees

Frontend deliverables in `lab-erp-demo`:

- AI chat page with streaming or progressive rendering behavior
- RAG search page and shortcut prompts
- downgrade messaging when AI falls back to RAG

## Acceptance Criteria

Each phase is accepted in this order:

1. backend formulas and rules verified
2. backend DTOs and response structure verified
3. backend transactional behavior verified
4. frontend integration completed against stable contracts
5. end-to-end page behavior verified for that phase

## Implementation Constraints

- Do not scatter finance implementation outside `erp-backend` and `lab-erp-demo`.
- Avoid introducing unnecessary global abstractions when module-local changes are enough.
- Preserve auditability and traceability over convenience.
- Keep AI features read-only with respect to financial ledgers.

## Out of Scope for Early Delivery

- Broad login redesign
- unrelated legacy-module refactors
- non-finance product flows outside the finance console rebuild

## Next Step

Create a written implementation plan from this design, broken down by phase, backend-first, with concrete deliverables for `erp-backend` and follow-up frontend integration work in `lab-erp-demo`.
