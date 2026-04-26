# T-004 Spring Boot finance core domain split plan

## Scope baseline

- Runtime stays inside `com.smartlab.erp.finance` as defined by `erp-backend/docs/finance-module-boundary-plan.md`.
- This task only splits backend implementation work; it does not add new controllers, entities, or DTOs yet.
- Shared ownership must stay minimal so parallel workers do not collide on common contracts.

## Shared ownership first

### Shared files owned by the first backend-contract worker before subdomain coding

- `erp-backend/src/main/java/com/smartlab/erp/finance/dto/**`: finance-only request and response DTOs.
- `erp-backend/src/main/java/com/smartlab/erp/finance/support/**`: response envelope, money/date serializers, pagination helpers, finance error catalog.
- `erp-backend/src/main/java/com/smartlab/erp/finance/entity/**`: only finance-owned aggregates after T-003 mapping lands.
- `erp-backend/src/main/java/com/smartlab/erp/finance/repository/**`: finance repositories mapped to `fin_*` and bridge tables.

### Cross-domain reuse rules

- Reuse `SecurityConfig`, JWT chain, `User`, `UserRepository`, and `RbacService` patterns as-is.
- Reference `SysProject` by narrow foreign key or bridge only; do not add wallet, clearing, dividend, or batch fields onto `SysProject`.
- Reuse `FileService` only for evidence upload flows; file metadata remains outside finance business calculations.
- Treat `MiddlewareAsset` and `MiddlewareRoyaltyRoster` as read-side anchors for clearing royalty enrichment until T-003 finalizes table mapping.

## Backend subdomain tasks

### F-OVR Finance overview

- Scope: overview controller, aggregate query service, read models for dashboard cards, bank snapshot summary, pending actions summary.
- Depends on: shared response envelope, T-002 response contract, T-003 venture/project bridge decision.
- Writes/reads: `finance/controller/FinanceOverviewController`, `finance/service/FinanceOverviewService`, overview DTOs only.
- Validation: controller slice for `GET /api/finance/overview`, repository/service test covering money precision and empty-state response.
- Lock guidance: lock overview controller/service/dto files only; do not touch wallet or clearing DTOs.

### F-WAL Wallet and transaction ledger

- Scope: wallet balance query, transaction list query, bank balance snapshot write path, audit filters, transaction type mapping.
- Depends on: shared money/date serializer, T-002 pagination and filter contract, T-003 wallet and transaction table mapping.
- Writes/reads: `finance/controller/FinanceWalletController`, `finance/service/WalletService`, wallet/transaction DTOs, wallet repositories.
- Validation: controller/service tests for `GET /api/finance/wallets`, `GET /api/finance/transactions`, `POST /api/finance/bank_balance`; precision and filter assertions required.
- Lock guidance: owns wallet DTO namespace and wallet repositories; other workers consume but do not edit them.

### F-BAT Cost batch execution and preview

- Scope: batch preview query, batch execution command, ledger-month validation, idempotency guard, cost ledger persistence orchestration.
- Depends on: T-002 batch request contract, T-003 cost and ledger batch mapping, shared finance exception catalog.
- Writes/reads: `finance/controller/FinanceBatchController`, `finance/service/CostBatchService`, batch DTOs, ledger repositories.
- Validation: service tests for month format `YYYY-MM`, preview aggregation, duplicate-run protection, and transactional rollback on partial failure.
- Lock guidance: exclusive lock on batch DTOs and service; clearing worker may read preview outputs but not change batch command contracts.

### F-CLR Clearing and middleware royalty settlement

- Scope: venture clearing query, clearing execution command, profit/loss carry logic, middleware royalty detail expansion, settlement audit outputs.
- Depends on: T-003 clearing, loss-transfer, and middleware mapping; batch outputs available for read; shared error codes.
- Writes/reads: `finance/controller/FinanceClearingController`, `finance/service/ClearingService`, clearing DTOs, royalty read adapters.
- Validation: integration-style service test for positive-profit, negative-profit, and royalty-split scenarios; controller slice for `GET /api/clearing/ventures` and `POST /api/clearing/execute`.
- Lock guidance: owns clearing DTOs and service logic; reads `MiddlewareRoyaltyRoster` via adapter, without modifying research package entities.

### F-DIV Dividend prepare, list, and confirm

- Scope: dividend sheet prepare pipeline, dividend list query, confirm state transition, confirm audit timestamps, wallet posting hook.
- Depends on: clearing result availability, T-002 dividend state contract, T-003 dividend sheet/table mapping.
- Writes/reads: `finance/controller/FinanceDividendController`, `finance/service/DividendService`, dividend DTOs, dividend repositories.
- Validation: service tests for `PENDING -> CONFIRMED` only, repeat-confirm rejection, amount precision, and posting handoff to wallet transaction writer.
- Lock guidance: owns dividend DTOs/status enum; wallet worker exposes posting interface but should not edit dividend state rules.

### F-ADJ Manual adjustment and audit trail

- Scope: manual adjustment create/list flows, validation rules, evidence linkage, audit actor capture, wallet/clearing side effects where contract requires.
- Depends on: shared error catalog, T-002 adjustment contract, T-003 adjustment table mapping, file capability reuse rule.
- Writes/reads: `finance/controller/FinanceAdjustmentController`, `finance/service/AdjustmentService`, adjustment DTOs, adjustment repositories.
- Validation: controller/service tests for required fields, negative/positive amount rules, evidence reference validation, and audit visibility in list responses.
- Lock guidance: owns adjustment DTOs/service/repositories; file upload controller remains outside its lock unless evidence API is explicitly extended.

## Recommended execution order

1. Shared support package: response envelope, finance error catalog, money/date serializers, pagination helper.
2. F-WAL and F-BAT in parallel once shared support lands and T-003 mapping is available.
3. F-OVR after wallet/batch read models stabilize so overview can compose existing aggregates instead of duplicating queries.
4. F-CLR after batch outputs and royalty mapping are testable.
5. F-DIV after clearing result contracts are fixed.
6. F-ADJ after wallet posting and audit support are ready.

## Verification plan

- Unit or slice tests per controller/service pair under `src/test/java/com/smartlab/erp/finance/**`.
- One focused integration path per stateful domain: batch execute, clearing execute, dividend confirm, adjustment create.
- Contract verification against T-002 for response envelope, enum visibility, money scale, date serialization, pagination keys, and error body shape.
- Access-control checks must reuse authenticated `/api/**` policy and method-level authorization.

## Current risks

- T-003 is still the gating source for actual finance entity/repository shapes, so subdomain workers should avoid creating tables or entity fields before that mapping is approved.
- Shared DTO/support ownership must be assigned before parallel coding starts, otherwise wallet, clearing, and dividend work will conflict in the same package.
- `SysProject.projectId` is string-based while legacy finance identifiers are numeric, so every write-path task must depend on the bridge strategy rather than inventing local conversions.
