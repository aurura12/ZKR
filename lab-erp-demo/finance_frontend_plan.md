# Finance Frontend Integration Plan (T-006)

## 1. Goal and boundary
- `lab-erp-demo` keeps the current Vue 3 + Element Plus + Pinia + Vue Router stack and adds a finance domain without changing existing global interfaces.
- All finance pages face Spring Boot APIs only; no page should depend on Flask response shapes or old Flask route names.
- Existing auth and transport infrastructure stays in place: `src/utils/request.js` keeps JWT injection and response unwrapping, `src/stores/userStore.js` keeps login state, and `src/router/index.js` keeps the global auth guard.
- T-002, T-004, and T-005 are now fixed upstream inputs for this plan, so the frontend landing points can align directly to the approved route names, finance envelope, backend subdomains, and AI/RAG contracts while still using a thin adapter layer for local field mapping.

## 2. Existing code constraints already confirmed
- `src/utils/request.js` returns `response.data` directly, so finance services receive the finance envelope body and must unwrap `data` in a finance-scoped adapter instead of assuming domain payloads arrive flat.
- `src/stores/userStore.js` currently stores `token` and `userInfo` from `/api/auth/login` and `/api/auth/me`; finance stores should not duplicate auth state.
- `src/router/index.js` already uses `meta.requiresAuth` and `meta.allowedRoles`; finance routing should reuse the same pattern instead of adding a second guard system.
- `erp-backend/src/main/java/com/smartlab/erp/entity/SystemRole.java` only exposes `ADMIN`, `BUSINESS`, `DATA`, `DEV`, and `ALGORITHM`, so frontend planning must not invent a new `FINANCE` or `MANAGER` role.

## 3. Recommended directory split
```text
src/
  api/
    finance/
      overview.js
      wallets.js
      workbench.js
      ai.js
  stores/
    financeOverviewStore.js
    financeWalletStore.js
    financeWorkbenchStore.js
    financeAiStore.js
  router/
    financeRoutes.js
  views/finance/
    FinanceShell.vue
    FinanceOverviewView.vue
    FinanceWalletsView.vue
    CostBatchView.vue
    ClearingCenterView.vue
    DividendCenterView.vue
    AdjustmentCenterView.vue
    RagSearchView.vue
    FinanceAiChatView.vue
  components/finance/
    overview/
    wallets/
    workbench/
    rag/
    ai/
  utils/
    financeFormatters.js
    financeAdapters.js
    financeEnums.js
```

Reasoning:
- Split `api` by subdomain so T-004 backend task outputs can map to isolated frontend files.
- Keep finance stores separate from `userStore` to avoid leaking domain state into global auth state.
- Add one `financeAdapters.js` module to absorb contract drift from T-002/T-005 without forcing page-level rewrites.

## 4. Router landing plan
Add one top-level finance shell and keep all child pages lazy-loaded.

```js
{
  path: '/finance',
  component: () => import('@/views/finance/FinanceShell.vue'),
  meta: { requiresAuth: true, allowedRoles: ['ADMIN', 'BUSINESS', 'DATA'] },
  children: [
    { path: '', redirect: '/finance/overview' },
    { path: 'overview', name: 'finance-overview', component: () => import('@/views/finance/FinanceOverviewView.vue') },
    { path: 'wallets', name: 'finance-wallets', component: () => import('@/views/finance/FinanceWalletsView.vue') },
    { path: 'cost-batches', name: 'finance-cost-batches', component: () => import('@/views/finance/CostBatchView.vue') },
    { path: 'clearing', name: 'finance-clearing', component: () => import('@/views/finance/ClearingCenterView.vue') },
    { path: 'dividends', name: 'finance-dividends', component: () => import('@/views/finance/DividendCenterView.vue') },
    { path: 'adjustments', name: 'finance-adjustments', component: () => import('@/views/finance/AdjustmentCenterView.vue') },
    { path: 'rag', name: 'finance-rag', component: () => import('@/views/finance/RagSearchView.vue') },
    { path: 'ai', name: 'finance-ai', component: () => import('@/views/finance/FinanceAiChatView.vue') }
  ]
}
```

Notes:
- `ADMIN` and `BUSINESS` should cover finance operations by default.
- `DATA` is included because the current target ambition still couples RAG/AI and finance-domain query views; remove it later if T-005 defines narrower authorization.
- Do not reuse `WorkspaceView.vue` or `ManagerDashboard.vue` as finance containers; a dedicated `FinanceShell.vue` keeps the change local.

## 5. API service checklist
Create thin service wrappers that mirror planned Spring domains instead of mixing every request into one file.

| File | Methods | Backend dependency |
|---|---|---|
| `src/api/finance/overview.js` | `getFinanceOverview()` | T-002 finance overview contract |
| `src/api/finance/wallets.js` | `getFinanceWallets(params)`, `getFinanceTransactions(params)`, `saveBankBalance(payload)` | T-002 wallet and bank snapshot contract |
| `src/api/finance/workbench.js` | `runCostBatch(payload)`, `getCostBatchPreview(ventureId, ledgerMonth)`, `getClearingVentures(params)`, `executeClearing(payload)`, `prepareDividendSheet(payload)`, `getDividendSheets(params)`, `confirmDividendSheet(payload)`, `createAdjustment(payload)`, `getAdjustmentLogs(params)` | T-002 + T-004 |
| `src/api/finance/ai.js` | `rebuildFinanceRag(payload)`, `queryFinanceRag(payload)`, `chatWithFinanceAi(payload)`, `resetFinanceAi(payload)` | T-005 |

Rules:
- Service files should only call `request`; any envelope or field-shape translation belongs in `src/utils/financeAdapters.js`.
- Read-only list pages should accept a single `params` object so pagination and filter contracts can settle later without changing page code.
- `getCostBatchPreview` is the one explicit exception: it should keep `ventureId` as a path argument and always require a separate `ledgerMonth` query input to match T-002 exactly.
- Mutation methods should return the finance envelope unchanged and let stores or adapters decide how to unwrap `data`, `meta`, and `error`.

## 6. Store split
- `financeOverviewStore.js`: overview cards, cash summary, bank snapshot, page-entry loading/error state.
- `financeWalletStore.js`: wallet list, current filter set, selected wallet, transaction pagination, export-ready query state.
- `financeWorkbenchStore.js`: cost batch preview/run result, including the user-selected required `ledgerMonth` that must be sent with batch preview requests, plus clearing result, dividend draft/confirm state, and adjustment create/list state.
- `financeAiStore.js`: RAG query records, AI message list, active context mode, reset/loading state.

Store rules:
- Keep one `load*` action per route entry and one `submit*` action per mutation flow.
- Persist only UI-relevant filters locally; do not cache large financial datasets in `localStorage`.
- Keep enum dictionaries in `src/utils/financeEnums.js` so they can be regenerated from T-002/T-004 outputs without touching views.

## 7. Page-to-contract mapping
| Page | Main use | Target API group | Core components |
|---|---|---|---|
| `FinanceOverviewView.vue` | Read-only finance summary | overview | KPI cards, statement panels, reconciliation card |
| `FinanceWalletsView.vue` | Wallet and transaction audit | wallets | wallet table, transaction table, filter bar |
| `CostBatchView.vue` | Batch preview and execution | workbench-cost | run form with required `ledgerMonth`, preview table, result panel |
| `ClearingCenterView.vue` | Clearing selection and execution | workbench-clearing | venture selector, execution form, summary cards |
| `DividendCenterView.vue` | Draft/confirm dividend sheets | workbench-dividend | status tabs, sheet table, confirm panel |
| `AdjustmentCenterView.vue` | Manual adjustment create/list | workbench-adjustment | create dialog, audit table |
| `RagSearchView.vue` | Finance knowledge query | ai-rag | mode switch, query form, cited result list |
| `FinanceAiChatView.vue` | Multi-turn finance assistant | ai-chat | message panel, context sidebar, composer |

This keeps page ownership aligned with backend subdomains and allows T-004/T-005 deliverables to land without reshaping router or store boundaries.

## 8. Shared UI and data rules
- Use lazy-loaded routes for every finance page to avoid increasing initial login or dashboard bundle size.
- Keep currency rendering at two decimals and centralize it in `src/utils/financeFormatters.js`.
- Use tables for audit-heavy views and drawers/dialogs for mutation flows so mobile fallbacks remain local to each page.
- Add a finance-specific error mapper above raw Axios errors to normalize `401`, `403`, `404`, `409`, `422`, `429`, and backend business exceptions into UI-safe messages.
- Keep skeleton/loading state at the page shell level; component children should receive ready-to-render data from stores.

## 9. Fixed inputs and remaining open items
- T-002 is a fixed input: finance pages should implement against the approved Spring route names, finance-wide `status`/`message`/`data`/`meta`/`timestamp`/`traceId` envelope, and shared pagination/date serialization rules without waiting for more contract changes.
- T-004 is a fixed input: the frontend keeps `overview.js`, `wallets.js`, `workbench.js`, and `ai.js` aligned to the approved backend subdomain split, with `workbench.js` covering cost batch, clearing, dividend, and adjustment flows until a later implementation task proves a further split is necessary.
- T-005 is a fixed input for AI/RAG wiring: finance adapters should unwrap `/api/finance/rag/rebuild`, `/api/finance/rag/query`, `/api/finance/ai/chat`, and `/api/finance/ai/reset` through the shared finance envelope, then map `mode`, `contextMode`, `contextBlocks`, `data.history`, and reset idempotency into store-friendly state.
- The only unresolved frontend planning item is authorization narrowing: current backend roles do not include a dedicated finance role, so route access should stay on existing roles until a separate RBAC decision changes the shared auth model.

## 10. Recommended implementation order
1. Add `src/router/financeRoutes.js` and `FinanceShell.vue`.
2. Build read-only pages first: `FinanceOverviewView.vue`, then `FinanceWalletsView.vue`.
3. Add transactional workbench pages in this order: cost batch, clearing, dividend, adjustment.
4. Finish `RagSearchView.vue` and `FinanceAiChatView.vue` after the finance adapter and markdown rendering path are in place.
