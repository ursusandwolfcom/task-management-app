# Handoff — ProjectFlow / S35 (2026-11-03)

> Historical S35 handoff. Its branch state, next steps and deferred items describe 2026-11-03, not current delivery. Current rules in AGENTS.md take precedence.

For Codex / other agents. Read `AGENTS.md` first. Workflow: `sprint/sNN` → `dev` → `main`.
Current communication language: Russian, as required by `AGENTS.md`.
Do **not** spawn sub-agents unless asked. No force-push / history rewrite. Do not commit `.env`. `/docs/` is gitignored (local only).

## Goal right now

Finish **S35 Email** into `dev`, then decide release `dev`→`main` (batch OK; user often wants `main≡dev`).

## Exact git state

| Ref | SHA | Notes |
|-----|-----|--------|
| `origin/main` | `4d20ec2` | Release #65 S32–S34; `main` ≡ `dev` **until S35 merges** |
| `origin/dev` | `4d20ec2` | Same tip |
| `origin/sprint/s35` | tip includes this file | Was `8774957` + handoff commit; check `git log -1` |
| Worktree | `F:\Projects\Java\task-management-app-s35` | Linked branch `sprint/s35` |
| Main clone | `F:\Projects\Java\task-management-app` | On `dev` @ `4d20ec2` |

**PR:** https://github.com/ursusandwolfcom/task-management-app/pull/66  
- Base: `dev` · Head: `sprint/s35` @ `8774957`  
- Title: S35: email notifications + shared outbox projection  
- CI on this SHA was **in progress** when handoff written (runs `41614072056` PR / `41614062227` push). Older runs on `93720e5` were **cancelled** (superseded by docs push).

### Commits on sprint/s35 (vs origin/dev)

1. `76814f6` feat(s35): email outbox channel with shared push projection  
2. `93720e5` fix(s35): disable mail health when SMTP unset  
3. `8774957` docs(s35): README changelog and SPRING_MAIL env  

## What S35 implements

- Liquibase **014**: `outbox_messages.email_processed_at`, `users.email_enabled`
- Triple outbox markers: in-app / telegram / email claim+mark
- Module `integration/email`: `EmailSender` Strategy, `EmailService`, `EmailController`, prefs via identity
- **DRY:** `OutboxPushProjection` shared by Telegram + Email (recipient + event text); channel services keep claim/mark/config
- Strategy for transport only: `EmailSender` / `TelegramBotClient`
- Frontend: Notifications UI email mute; `verify-container` changelog count **14**
- SMTP: optional `SPRING_MAIL_HOST` (+ port/user/password); `MAIL_FROM`; **omit host to disable**
- `management.health.mail.enabled=false` — empty mail must not fail `/actuator/health`

### Critical bug already fixed

First CI failed at **Check packaged startup and restart** (`Application did not become healthy`).  
Cause: `spring.mail.host=${MAIL_HOST:}` created empty-host `JavaMailSender` → mail health / readiness DOWN.  
Fix in `93720e5`: remove empty host binding; disable mail health; document `SPRING_MAIL_*`.

## Verification done locally

- Targeted: `OutboxPushProjectionTest`, `EmailIntegrationTest`, `TelegramIntegrationTest`, `ArchitectureRulesTest` → green  
- `mvnw -Dworkspace.prebuilt=true verify` → green (`verify_exit=0`)  
- Full `clean verify` without `node_modules` fails in worktree if junction target empty — run `npm ci` in main repo or rely on CI for workspace build  

## Next steps (in order)

1. Wait CI green on **exact** SHA `8774957` (PR #66). If red: fix on `sprint/s35`, push, re-wait **same** SHA.  
2. Merge PR #66 → `dev` (merge commit; AGENTS authorizes cycle merges).  
3. Delete `sprint/s35` local+origin after merge; sync worktrees.  
4. Smoke/verify on latest `dev`.  
5. Release PR `dev`→`main` when batch ready (S35 alone OK if user wants consistency). Sync `dev` with `main` after.  
6. Update local `docs/CHANGELOG.md` merge/release SHAs (already drafted under S35).

## Do not

- Force-push / rewrite shared history  
- Push untested fixes to `dev`/`main`  
- Treat alpha browser storage as real CRUD  
- Reintroduce empty `spring.mail.host=` default in `application.properties`  
- Promise Dropbox / HTML digests / hard leveling (Deferred)

## Local docs (ignored)

| File | Role |
|------|------|
| `docs/HANDOFF_S35.md` | This file |
| `docs/CHANGELOG.md` | Iteration log through S35 |
| `docs/CHANGELOG_UML.puml` | Iteration diagram incl. S35 |
| `docs/EMAIL_DECISIONS.md` | Confirmed email decisions + Strategy/DRY |
| `docs/TELEGRAM_DECISIONS.md` | Telegram (done S31) |
| `docs/DROPBOX_DECISIONS.md` | Still pending user OK |
| `docs/UNIFIED_PLAN.md` / `ARCHITECTURE.md` / `DEVELOPMENT_SPRINTS.md` / `WORKFLOW.md` | Plans if present |

## Skills (project)

Prefer: `java-developer`, `java-architect`, `frontend-react`, `requirements-spec-writer`, TaskGraph-* under Codex skills. Do **not** use removed generic `files.zip` skills.

## Parallel note from session

User asked Strategy to avoid Telegram/Email duplication → answered yes: shared projection + Strategy for send only. Deadline pressure: push landed; merge blocked on CI time.
