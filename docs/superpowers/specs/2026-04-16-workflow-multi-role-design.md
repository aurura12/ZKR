# Workflow Multi-Role Design

> Historical note: this design introduces a unified workflow-role mapping layer for project, product, and research flows. It is intended to preserve the current login/auth model and only expand role assignment inside workflow membership and selection UIs.

**Goal:** Allow the same user to appear multiple times in workflow selection flows when they hold multiple workflow roles, without changing system login authorization.

**Architecture:** Keep `sys_user` as the single-source-of-truth for account identity and login authorization, and introduce a new workflow-scoped role mapping table that stores `workflow_type`, `workflow_id`, `user_id`, and `role`. Read paths prefer the new mapping table, while legacy workflow tables remain as a fallback during the migration window. Frontend selection lists render the same person once per workflow role.

**Tech Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, Vue 3, Pinia, Axios

---

## Context

- `sys_user.role` currently represents the system/login role and drives `UserDetails.getAuthorities()` and JWT-authenticated access.
- The current workflow models already store role-bearing membership in flow-specific places such as `sys_project_member.role`, `ResearchProjectProfile` owner fields, and product promotion owner fields.
- The requested behavior is workflow-scoped only: a user must be able to show up multiple times in the workflow selection UI as `user + role` combinations such as `张三 / DATA`, `张三 / DEV`, `张三 / RESEARCH`.
- Login authorization, route guards, and `@PreAuthorize` behavior must remain unchanged.

## Goal

Introduce a unified workflow role mapping layer so that workflow member selection, display, and persistence can represent multiple roles per user within the same workflow, while leaving the system user model and authentication untouched.

## Non-Goals

- Changing `sys_user.role` into a multi-role field
- Modifying login authentication or JWT authority derivation
- Changing `@PreAuthorize` semantics
- Reworking the finance/ERP domain model
- Introducing SSO or a new global RBAC system

## Chosen Approach

Use a new workflow-scoped mapping table as the canonical source for workflow role assignments.

- Keep `sys_user` unchanged.
- Add a new table, recommended name `workflow_member_role`, to store workflow-specific role assignments.
- Treat the tuple `workflow_type + workflow_id + user_id + role` as the unique identity of a workflow assignment.
- During the transition period, write both the legacy workflow tables and the new mapping table.
- Read paths should prefer the new table, and fall back to legacy sources only when the new table has not yet been populated for a workflow.

## Why This Approach

- It keeps login/auth isolated from workflow responsibilities.
- It works uniformly across project, product, and research flows.
- It allows the same user to be rendered multiple times as separate selectable workflow identities.
- It reduces future duplication by making all flow-specific role logic derive from one canonical mapping table.

## Scope

### In Scope

- New workflow role mapping table and repository layer
- Migration of existing project/product/research workflow role data into the new table
- Dual-write during rollout
- Read-new-first behavior with legacy fallback
- Frontend selection list expansion so the same user appears once per workflow role
- Workflow member display and selection logic in project, product, and research screens

### Out of Scope

- Changing system account login roles
- Changing access control or route authorization
- Replacing existing user profile or account-domain behavior
- Designing a general-purpose org chart or HR system

## Current State

### User model

`sys_user` currently contains a single `role` column and `User.getAuthorities()` returns one `ROLE_...` value derived from that field.

Relevant file:

- `erp-backend/src/main/java/com/smartlab/erp/entity/User.java`
- `erp-backend/src/main/java/com/smartlab/erp/security/JwtAuthenticationFilter.java`

### Legacy workflow role storage

The existing system spreads workflow role data across multiple places:

- `sys_project_member.role` for project and team membership
- `ResearchProjectProfile` for research owner fields such as `hostUserId`, `chiefEngineerUserId`, and document owners
- Product flow owner fields such as promotion IC and demo file owners

Because these are flow-specific and not standardized, the same user can be represented inconsistently across views.

## Proposed Data Model

### New table: `workflow_member_role`

Recommended columns:

- `id` BIGSERIAL primary key
- `workflow_type` VARCHAR(32) not null
- `workflow_id` VARCHAR(64) not null
- `user_id` VARCHAR(64) not null
- `role` VARCHAR(64) not null
- `created_at` TIMESTAMP not null
- `updated_at` TIMESTAMP nullable
- optional `source` VARCHAR(32) to record whether the row originated from legacy migration, manual edit, or UI selection

Recommended unique constraint:

- `workflow_type + workflow_id + user_id + role`

This allows the same `user_id` to appear multiple times in the same workflow as long as the `role` differs.

### Read model

Workflow selection UIs should query a normalized list of workflow-role assignments and render each record as a separate selectable item.

Example output:

- `张三 / DATA`
- `张三 / DEV`
- `张三 / RESEARCH`

### Write model

When a user assigns a role in a workflow UI, the backend should persist one row per `user_id + role` combination in `workflow_member_role`. Legacy writes remain in place during transition until the new model is fully validated.

## Workflow Type Semantics

The `workflow_type` field should use stable identifiers, not UI labels.

Recommended values:

- `PROJECT`
- `PRODUCT`
- `RESEARCH`

These align with the existing workflow categories used across the ERP system.

## Migration Strategy

### Phase 1: Backfill historical data

Backfill existing workflow roles into `workflow_member_role` from current data sources:

- project membership rows from `sys_project_member`
- research owner fields from `ResearchProjectProfile`
- product owner/member fields from `ProductIdeaDetail` and related product workflow tables

The backfill must preserve the original workflow meaning of each role and create one row per role-bearing assignment.

### Phase 2: Dual-write

For a transition period:

- legacy workflow tables continue to be written as before
- `workflow_member_role` is written in parallel

This reduces risk while the new read path is validated.

### Phase 3: Read-new-first

Selection and display logic should read from `workflow_member_role` first.

- If matching rows exist in the new table, render them.
- If no matching rows exist yet for a workflow, fall back to the legacy source.

### Phase 4: Decommission legacy role reads

After validation, workflow role reads can be moved fully to the new table, leaving legacy tables only for compatibility or archival data until they are no longer needed.

## Frontend Design

### Selection list expansion

Any workflow member selector that currently renders unique users should be updated to render normalized workflow-role rows instead.

The rendered label should combine:

- user display name
- workflow role label

Example:

- `焦淼 / HOST`
- `焦淼 / BLUEPRINT_OWNER`
- `胡军 / CHIEF_ENGINEER`

### Repeated selection behavior

The same `user_id` should be allowed to appear multiple times in the same selection UI if the roles differ.

The frontend should not deduplicate by `user_id` alone when the current screen is working with workflow-role mappings.

### Existing screens affected

- research creation and research workflow screens
- project team setup and member scheduling screens
- product promotion setup and owner assignment screens

## Backend Design

### Repository layer

Add a repository for the new mapping table and query helpers that support:

- fetch all roles for a workflow
- fetch all workflows for a user
- upsert by `workflow_type + workflow_id + user_id + role`
- delete a specific role mapping

### Service layer

Add a workflow role service that:

- normalizes legacy assignments into the new table
- handles dual-write during transition
- exposes normalized role lists for frontend consumption
- resolves display names by joining to `sys_user`

### API layer

Add or extend endpoints that return normalized workflow-role items rather than unique user items when the screen needs multi-role display.

## Error Handling

- Duplicate workflow-role submissions should be treated as idempotent updates, not hard failures.
- Missing user references should be rejected early with a clear validation message.
- If the new table is partially backfilled, read-new-first with legacy fallback must avoid showing duplicate entries for the same logical role.
- Migration and dual-write paths should log counts so mismatches can be audited.

## Testing

### Backend tests

- verify the same `user_id` can be assigned multiple roles in the same workflow
- verify the same `user_id + role` cannot be inserted twice for the same workflow
- verify read-new-first returns the new normalized records
- verify legacy fallback works when the new table has no rows yet

### Frontend tests

- verify selection lists render one entry per workflow-role mapping
- verify duplicate `user_id` values remain visible when roles differ
- verify submission payloads preserve role identity

## Acceptance Criteria

- A workflow can store multiple role assignments for the same user without changing login authorization.
- The same user can be shown multiple times in workflow selection UIs when their roles differ.
- Existing system authentication continues to work exactly as before.
- Historical workflow role data is migrated into the new table.
- New reads prefer the normalized table, with legacy fallback during the rollout window.

## Implementation Notes

- Do not reuse `sys_user.role` for workflow role duplication.
- Do not change JWT authorities or route-guard logic.
- Treat `workflow_member_role` as the canonical workflow-role source once the migration is complete.
