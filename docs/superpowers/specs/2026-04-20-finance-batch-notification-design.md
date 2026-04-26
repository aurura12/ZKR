# Finance Batch Notification Design

## Goal

Ensure every finance cost batch execution notifies Zhang Qi when the batch finishes.

The notification policy is:

1. Notify only after the batch finishes.
2. Cover all batch entrypoints, including scheduled runs and manual runs.
3. Try internal message first.
4. If internal message fails, fall back to SMTP email.
5. Notification failure must not break the batch itself.

## Scope

### In scope

- Finance labor cost batch completion notifications.
- Scheduled batch runs.
- Manual batch runs through the existing API.
- Startup-driven historical backfill runs, because they also execute the same batch flow.

### Out of scope

- A general notification preference system.
- Making the recipient configurable in this change.
- Changing login, authentication, or authorization behavior.
- Adding new message transport channels.

## Current State

- Finance batch execution is centralized in `FinanceCostBatchService.runBatch(...)`.
- Scheduled execution is triggered by `FinanceCostBatchScheduler`.
- Manual execution is exposed by `FinanceWorkbenchController`.
- Historical startup backfill also calls `FinanceCostBatchService.runBatch(...)`.
- Internal messages already exist through `InternalMessageService`.
- SMTP email already exists through `MailService`.

## Design

### 1. Notification trigger point

The notification must be attached to `FinanceCostBatchService.runBatch(...)`, not to the scheduler or controller.

This ensures one consistent notification path for:

- nightly scheduled runs
- manual API-triggered runs
- startup historical backfill runs

The service already knows whether the batch succeeded or failed and has the data needed for a useful result message.

### 2. Recipient resolution

The recipient is fixed to the Zhang Qi account.

The implementation should resolve the user by a fixed account identifier, using the existing user repository. The preferred identifier is `username = 'zhangqi'`.

If the Zhang Qi account is not found, the system must log an explicit error and skip notification without failing the batch.

### 3. Delivery strategy

Notification is sent only when the batch has finished, either successfully or unsuccessfully.

Delivery order:

1. Try internal message.
2. If internal message throws or fails, try SMTP email.
3. If both channels fail, log the failure and return control to the batch flow.

This preserves the requirement that every batch execution attempts to notify Zhang Qi, while also ensuring the batch is never marked failed only because notification delivery failed.

### 4. Message content

The notification content should be concise and operational.

For success, include:

- ledger month
- batch status
- batch id if available
- generated record count

For failure, include:

- ledger month
- failure status
- error summary

The internal message type should be finance-specific and new, for example `FINANCE_BATCH_RESULT`.

The email subject should clearly indicate whether the batch succeeded or failed.

### 5. Failure handling

Notification failures must be contained.

- Internal message failure should be logged, then trigger SMTP fallback.
- SMTP failure should also be logged.
- The original batch result must still be returned to the caller, or the original batch exception must still be thrown.

This means notification is best-effort with required attempt semantics, not a hard dependency of batch execution.

## Data Flow

1. A batch is triggered from scheduler, API, or startup backfill.
2. `FinanceCostBatchService.runBatch(...)` executes the batch.
3. When execution ends, it builds a result notification payload.
4. The system resolves the fixed Zhang Qi account.
5. It sends an internal message first.
6. If that fails, it sends an SMTP email.
7. If both fail, the failure is logged and the batch flow continues.

## Error Handling

- If Zhang Qi does not exist in `sys_user`, log and skip notification.
- If Zhang Qi has no email, internal message still works; email fallback should log that no email address is available.
- If batch execution throws, the failure notification should still be attempted before rethrowing the batch exception.
- If the notification formatter itself fails, log and do not alter batch completion semantics.

## Testing

- Success path sends internal message and does not send email.
- Internal message failure causes email fallback.
- Double failure does not suppress the batch response or replace the original batch exception.
- Manual API-triggered runs notify through the same service path.
- Scheduled runs notify through the same service path because they share `runBatch(...)`.
- Login and authority behavior remain unchanged.

## Notes on Implementation Strategy

The smallest correct implementation is to add one finance-specific notification service and call it from `FinanceCostBatchService.runBatch(...)` in both the success and exception paths.

This avoids duplicating notification logic across the scheduler, controller, and backfill runner, and it guarantees coverage of all current batch entrypoints.
