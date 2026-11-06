# What changed: notification delivery

Review scope: the unmerged S56 candidate in [PR #99](https://github.com/ursusandwolfcom/task-management-app/pull/99).
Main/dev remain at the previous release; open the PR branch to inspect these changes.
This extends the approved reminder/delivery work. For the original assignment and model
mapping, start with [the assignment review](MATE_ASSIGNMENT_REVIEW.md).

## Read in this order

| Entry | What to check |
|---|---|
| [Composition-root scheduler](../src/main/java/com/example/taskmanagement/config/NotificationDeliveryScheduler.java) | Every 30 seconds, bounded batches, channel failure isolation |
| [Dispatch port](../src/main/java/com/example/taskmanagement/integration/outbox/api/OutboxDispatchOperations.java) and [coordinator](../src/main/java/com/example/taskmanagement/integration/outbox/application/OutboxDispatcher.java) | One event transaction; rollback before separate failure-recording transaction |
| [Outbox service](../src/main/java/com/example/taskmanagement/integration/outbox/application/OutboxService.java) and [queries](../src/main/java/com/example/taskmanagement/integration/outbox/persistence/OutboxMessageRepository.java) | Row locks, deadlines, no reopening acknowledged channels |
| [Retry policy](../src/main/java/com/example/taskmanagement/integration/outbox/domain/DeliveryRetryPolicy.java) and [migration 025](../src/main/resources/db/changelog/changes/025-outbox-retry-timing.yaml) | Approved 1/5/15/60-minute delays, persistence across new transactions |
| [Email service](../src/main/java/com/example/taskmanagement/integration/email/application/EmailService.java) | Preferences, current-recipient guards, immediate versus digest delivery, selected-row cleanup |
| [Retry scenarios](../src/test/java/com/example/taskmanagement/integration/outbox/OutboxRetryIntegrationTest.java) and [email scenarios](../src/test/java/com/example/taskmanagement/integration/email/EmailIntegrationTest.java) | Failure rollback, later-event progress, real service wiring using fake provider, concurrent digest inserts |

## Before and after

- Previously, browser/channel consumption processed a whole batch in one transaction.
  A provider failure could roll back other events and repeatedly block the first row.
  Each event now commits or rolls back independently; failure timing is stored after
  rollback, and the dispatcher can continue with later eligible events.
- Browser polling and background dispatch now share the same path. Retrying through
  the browser cannot bypass stored next-attempt timestamps. The scheduler runs without
  an open browser; the existing daily email-digest schedule is unchanged.
- Earlier review fixes added exclusive outbox claims and changed digest cleanup from
  deleting every user row to deleting only the selected batch. New inserts during an
  email send remain queued for the next digest.

```text
NotificationDeliveryScheduler [config / composition root]
    | NotificationOperations / EmailOperations / TelegramOperations
    v
Channel service ----> OutboxDispatchOperations ----> OutboxDispatcher
    ^                                               |
    | delivery callback                            | REQUIRES_NEW
    +-----------------------------------------------+
                                                    claim + deliver + acknowledge
                                                    |
                                               failure / rollback
                                                    |
                                               separate transaction
                                                    defer + persisted deadline
```

All three channels reuse their existing formatting, preferences and access guards.
No new public HTTP endpoint or identity/task lifecycle behavior was introduced.
Configuration is documented in [README](../README.md#in-app-notifications).

## Review findings and limits

| Finding | Status and evidence |
|---|---|
| Concurrent outbox consumers could process the same event | Fixed in `5e4de86`; lock/acknowledgement regression retained |
| Digest cleanup could delete an unsent concurrent insert | Fixed in `a18a1f3`; independent insert during fake send remains for next flush |
| Retry timing had no durable storage | Persisted in `3e55a9d`; deadline-aware claims and channel independence scenarios |
| One failed event blocked later work | Isolated in `39f7b0b`; rollback/deferral/progress tests for all three channels |
| Scheduler was placed across forbidden module boundaries | Corrected in `321a2b7` by moving orchestration to `config`; architecture rules unchanged |
| Two digest flushes could send the same selected rows concurrently | Selected-entry write locks now span send and cleanup; concurrent flush and failed-send rollback scenarios in EmailIntegrationTest |

External delivery is at-least-once. A provider may accept a message before database
rollback; partial multi-recipient sends may duplicate delivery. Concurrent digest
flushes now wait on their selected rows; rows added during a send remain for the next
digest. This work does not claim exactly-once transport or measured scalability.
Tests use recording/failing fakes, not real messages or live-account acceptance.
AWS deployment and live/measured acceptance remain separate work.

## Verification and integration

Implementation commits: `39f7b0b`, `321a2b7`, on top of the earlier outbox/digest fixes.
Targeted dispatch/channel selection: 31 passed. Scheduler/email/OpenAPI selection:
21 passed. Clean architecture/scheduler selection after the placement correction:
6 passed. Commands used Maven Wrapper, Java 21 and UTC with Spring `.env` import disabled.

The first full run/CI on `39f7b0b` failed the architecture rule and is not green evidence.
Final full local/CI verification is being collected after the correction and publication.
Use the [latest PR checks](https://github.com/ursusandwolfcom/task-management-app/pull/99/checks)
for the exact candidate; older SHA success does not authorize integration. Expected
startup/restart history is 25 Liquibase migrations in `public`, with no new migration
on restart. Windows excludes two pre-existing Unix-only process tests.

Required external approval is still outstanding. Implemented and tested candidate code
is not merged, released, deployed, or verified against real providers merely because
it appears in this review packet.
