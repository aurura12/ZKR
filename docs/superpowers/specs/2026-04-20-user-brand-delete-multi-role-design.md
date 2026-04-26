# User Brand, User Deletion, and Multi-Role Design

## Goal

This change set does three things:

1. Rename the visible product brand from `智能博弈实验室` to `实验室`.
2. Remove the user whose display name is `瀧月` from the primary user table.
3. Support multiple role matches for the same user while keeping `sys_user.role` as the primary role.

The intent is to keep the data model small and consistent with the current schema. We reuse the existing `sys_user_role` table instead of introducing a new structure.

## Scope

### In scope

- Frontend brand text updates where the product name is visible.
- Safe one-time deletion of the `瀧月` user record.
- Cleanup of direct per-user rows that would otherwise become orphaned.
- Role modeling for users who need more than one matching role.

### Out of scope

- A general-purpose admin UI for deleting arbitrary users.
- A new attribute framework or key-value profile system.
- Changing the primary meaning of `sys_user.role`.

## Current State

- `User` is stored in `sys_user` and already has a single `role` field.
- `UserRole` already exists in `sys_user_role` with a unique constraint on `(user_id, role)`.
- Frontend branding is already centralized in a few visible UI locations.
- The application currently uses `role` checks in several places, so changing the primary role model would be high risk.

## Design

### 1. Brand rename

Replace the visible product name `智能博弈实验室` with `实验室` in the frontend surfaces that users see directly, including the document title and top-level branding text.

This is a text-only change. It does not affect authentication, APIs, or persisted data.

### 2. Delete `瀧月`

Delete the user by matching `sys_user.name = '瀧月'` after confirming there is exactly one match.

Before deleting the primary record, remove direct per-user records that are guaranteed to become orphaned and are safe to clean up in the same operation, such as:

- `user_badge`
- `sys_user_role`

Do not delete unrelated business history by default. Historical project, finance, and workflow data should remain intact unless it is directly tied to the same row family and is required to preserve integrity.

If more than one `sys_user` row matches the name, stop and require manual disambiguation.

### 3. Multi-role support

Keep `sys_user.role` as the user’s primary role.

Use `sys_user_role` to store any additional roles for the same user. A user may have any number of role matches as long as each `(user_id, role)` pair is unique.

Role checks should follow this model:

- Primary role continues to come from `sys_user.role`.
- Secondary roles are read from `sys_user_role`.
- A user matches a requested role if either the primary role or any secondary role matches the required role.
- Multi-role data must not change login authentication or JWT/`UserDetails` authority resolution. Those flows continue to use `sys_user.role` only.
- Any business-layer role lookup or UI filtering logic that checks a role may use the combined match rule instead of reading only `sys_user.role`.

This keeps existing code paths stable while allowing additive role membership.

## Data Flow

1. Frontend renders the new brand text.
2. Backend deletion script locates `瀧月` by display name.
3. The script deletes dependent direct rows first.
4. The script deletes the `sys_user` row.
5. Role assignment logic inserts additional rows into `sys_user_role` as needed.

## Error Handling

- If `瀧月` is not found, the deletion step is a no-op and should report that nothing was deleted.
- If multiple users match `瀧月`, the deletion step must fail fast and surface the ambiguity.
- If an additional role already exists for the same `(user_id, role)` pair, do not insert a duplicate row.

## Verification

- Confirm the frontend displays `实验室` rather than `智能博弈实验室`.
- Confirm `瀧月` is absent from `sys_user` after deletion.
- Confirm `user_badge` and `sys_user_role` rows for that user are removed as part of the cleanup.
- Confirm a single user can hold multiple roles via `sys_user_role` while still preserving `sys_user.role`.
- Confirm existing role checks continue to work for primary roles.

## Notes on Implementation Strategy

The safest implementation path is to keep the existing primary-role semantics untouched and extend role matching on top of `sys_user_role`. That avoids a broad migration across controllers, services, and UI permission checks.
