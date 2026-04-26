# T-001 Spring Boot finance boundary plan

## Decision

Adopt an in-repo `finance` domain package inside `erp-backend` instead of a separate Spring Boot application.
The finance package should sit beside the existing project, product, research, auth, file, and security packages so it can reuse the current runtime, JWT authentication chain, JPA datasource, transaction management, and deployment pipeline.
This keeps scheme A on a single Spring Boot runtime and avoids reintroducing Flask or creating cross-service consistency work before contracts are settled.

## Module placement

- Base package: `com.smartlab.erp.finance`
- Suggested subpackages:
  - `controller`: finance overview, wallet, batch, clearing, dividend, adjustment, rag, ai endpoints
  - `service`: domain orchestration and transaction boundaries
  - `dto`: request and response contracts owned by the finance domain
  - `entity`: only finance-owned aggregates or sidecar mappings that cannot be expressed through existing entities
  - `repository`: finance table access for `fin_*`, sidecar, and ledger relations
  - `support`: shared finance serializers, mappers, and policy helpers

## Reuse boundaries

### Reuse directly

- Authentication and identity: `SecurityConfig`, `JwtAuthenticationFilter`, `AuthService`, `User`, `UserRepository`
- Authorization patterns: `@PreAuthorize`, `RbacService` style checks, current-user extraction via `UserPrincipal`
- Persistence and runtime: existing Spring Boot app, `spring-boot-starter-data-jpa`, PostgreSQL datasource, transaction manager, Jackson config, validation stack
- File capability: `FileService` and `/api/files/**` for finance attachments such as settlement proof, bank evidence, or adjustment evidence
- Existing project context: `SysProject`, `SysProjectMember`, flow/status enums, research-side `MiddlewareAsset` and `MiddlewareRoyaltyRoster` as anchors for future royalty linkage

### Keep isolated inside finance domain

- Unified finance response envelope and finance error catalog
- Money/date serialization rules for financial APIs
- Batch cost, clearing, dividend, wallet, adjustment, RAG, and AI orchestration services
- Mapping rules from legacy `business_venture` and `fin_*` tables into Spring Boot read/write contracts

## Entity and aggregate strategy

- Do not overload `SysProject` with wallet, clearing, dividend, or bank snapshot fields.
- Treat `SysProject` as the master project identity that finance references when a legacy venture can be mapped to an existing project.
- Keep finance records in dedicated finance tables or finance-side entities, with a narrow reference back to `SysProject` or a finance-owned venture bridge once task T-003 finalizes the mapping.
- Reuse `User` as the finance actor and wallet owner identity instead of introducing a parallel user aggregate.

## Why not a separate Spring Boot finance service now

- Current code already centralizes JWT, CORS, datasource, file handling, and RBAC inside one application.
- A new service would immediately require duplicated auth, remote identity propagation, shared transaction design, and new deployment wiring before business contracts are stable.
- Task T-001 only needs a landing zone; an in-repo module gives the smallest invasive path and preserves a later extraction option if the finance domain outgrows the monolith.

## Initial implementation ownership

- `finance` owns financial APIs, DTOs, validation, and read/write orchestration.
- Existing `project/product/research` packages keep lifecycle and collaboration concerns.
- Shared cross-domain infrastructure remains under current `config`, `security`, `service`, and common exception areas unless duplication appears.

## Risks

- Legacy `business_venture.id` is BIGINT while `SysProject.projectId` is String UUID-like; a bridge or deterministic mapping is still unresolved.
- Current `application.yml` uses global Jackson date settings, but finance APIs need explicit timestamp and money serialization rules before frontend integration.
- Research-side middleware tables partially overlap with legacy royalty concepts, but ownership between research assets and finance clearing needs a contract before entity reuse.
- RAG and AI migration should be packaged under the same runtime boundary, but model gateway, vector storage, and rate limiting remain open design decisions.
