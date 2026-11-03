# ProjectFlow

> Планирование и выполнение командной работы: что делать дальше, кто отвечает, что блокирует сроки.

[![CI](https://github.com/ursusandwolfcom/task-management-app/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ursusandwolfcom/task-management-app/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](#local-development)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-модульный%20монолит-6DB33F?logo=springboot&logoColor=white)](#local-development)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Liquibase-4169E1?logo=postgresql&logoColor=white)](#local-development)
[![React](https://img.shields.io/badge/Workspace-React%20%2B%20TypeScript-61DAFB?logo=react&logoColor=black)](#local-development)

## Демо для заказчика

Готовый **рабочий демо-срез** на ветке `main`: MVP, планирование (праздники, обед), портфель, файлы, in-app и опциональные Telegram/Email. Это не production-deploy — честный объём см. [Deferred](#deferred-explicit-technical-debt).

### За 10 минут

| # | Что показать | Зачем |
|---|--------------|--------|
| 1 | Регистрация / вход (cookie-сессия) | Один аккаунт = одни данные в двух вкладках |
| 2 | Проект → участник → задачи **A → B** | Зависимости и права на живых данных |
| 3 | «Мои задачи»: B заблокирована, A готова | Очередь с причиной, не «магический» статус |
| 4 | Завершить A → B открывается; fail → retry | История попыток сохраняется |
| 5 | Gantt / what-if по оценкам | CPM Пн–Пт 09–17 + обед 13–14, сценарий без записи в БД |
| 6 | Обзор портфеля + загрузка исполнителей | Риск и нагрузка → переход к работе |
| 7 | Комментарий, метка, вложение, уведомление | Совместная работа в карточке задачи |

### Что уже в демо

```text
Аккаунт и проекты     Задачи и lifecycle     DAG / очередь / next
Комментарии и метки   Вложения (локальный диск)   In-app уведомления (outbox)
CPM + Gantt + what-if Портфель рисков + workload  Workspace UI (темы, CSRF)
```

### Что сознательно позже

Hard leveling и финансовые KPI — в таблице **Deferred**. Праздники, обед, soft contention, Telegram, Email v1 и Dropbox storage (опционально) уже поддерживаются.

### Как запустить демо

Полный пошаговый скрипт с таймингом: **[DEMO.md](DEMO.md)**.

```powershell
docker compose up -d --wait
.\mvnw.cmd -q -DskipTests package
java -jar target/task-management-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=workspace
# UI: http://127.0.0.1:8080/app/
```

Подробные контракты API — в разделах ниже. Alpha (`/alpha`) — отдельный браузерный прототип, не путать с серверным workspace.

---

# Technical reference

## Task lifecycle

The pure `task.domain` lifecycle supports start, complete, fail, cancel and retry.
Cancel is allowed from NOT_STARTED, IN_PROGRESS and FAILED; COMPLETED and CANCELLED
are terminal. `TaskExecution` retains immutable execution attempts with UTC timestamps:
retry preserves the failed attempt and the next start creates another. Start requires
actual predecessor completion supplied by the caller. The task API persists tasks and
attempts and checks live project access and actual dependency completion under the project lock.
Resource calendars and reservations remain a later stage.
The browser alpha remains a separate prototype.

## Persisted tasks

Authenticated project participants create and edit tasks through either
`POST/GET /api/tasks` (projectId in body/query) or
`POST/GET /api/projects/{projectId}/tasks`. Both routes use the same service;
conflicting project IDs are rejected. Tasks have name, description, priority
(LOW/MEDIUM/HIGH/CRITICAL), optional estimatedMinutes, dueDate and assigneeId.
Effort is a whole number from **15 to 525600 minutes**, inclusive: one year means
365 physical days, independent of the future working calendar. An assignee must
be a member of the task's project.

Tasks also have a workbench `bucket`: INBOX, INCUBATOR, WORK or ARCHIVE. The bucket
is independent of lifecycle status; create may place a task in any bucket (JSON omit
defaults to INBOX). Moves between buckets are free. Ready-queue queries and `start`
apply only to WORK; other buckets are triage/archive views.

`GET/PUT/PATCH /api/tasks/{id}` reads or updates metadata. Updates require the
task's expected version; projectId is immutable. PUT replaces optional metadata,
PATCH retains omitted fields and explicit null clears optional fields. Status,
factual dates, attempt history and unknown fields are rejected in request bodies.
Assignee changes are allowed only in NOT_STARTED or FAILED.
Malformed due dates return 400 VALIDATION_ERROR. PATCH checks both the version of
the metadata snapshot and the version under the write lock; a mismatch returns
409 VERSION_CONFLICT without changing omitted fields.

`POST /api/tasks/{id}/start|complete|fail|retry` takes `{"version":0}` and is
available to the assignee, owner or ADMIN. Start requires bucket WORK, an assignee
and every actual predecessor COMPLETED; otherwise 409 TASK_NOT_IN_WORK. Only owner
or ADMIN can cancel using `POST /api/tasks/{id}/cancel`. Terminal COMPLETED/CANCELLED
tasks cannot restart.
Retry preserves failed attempts; the next start creates another. UTC actualStart/
actualEnd describe the most recent attempt, including its retained facts after retry.
`GET /api/tasks/{id}/attempts` returns history newest first.

`DELETE /api/tasks/{id}?version=0` is owner/ADMIN only and accepts only a
task that has never started and has no dependent tasks (NOT_STARTED or cancelled before
its first start). History-bearing
tasks cannot be deleted even after retry. A project containing tasks cannot be
deleted; a member with any assigned task cannot be removed until reassignment or
unassignment. These checks share the same project lock as task writes.

Task/attempt lists accept page 0..1000000 and size 1..100 (defaults 0/20), newest
first. Project task lists accept optional filters `status`, `assigneeId`, `dueFrom`,
`dueTo`, `labelId` and `bucket` (due range requires a non-null dueDate; `dueFrom`
after `dueTo` is 400 VALIDATION_ERROR). `GET /api/tasks/mine` lists tasks assigned
to the caller across member projects (ADMIN: all projects), with the same filters
except `assigneeId` is always the authenticated user. Missing and inaccessible tasks
both return 404 TASK_NOT_FOUND. Conflicts return stable 409 codes: VERSION_CONFLICT,
ASSIGNEE_NOT_MEMBER, ASSIGNEE_REQUIRED, INVALID_TASK_TRANSITION, DEPENDENCIES_NOT_COMPLETED,
TASK_NOT_IN_WORK, ASSIGNMENT_CHANGE_FORBIDDEN, TASK_DELETE_FORBIDDEN and
TASK_HAS_DEPENDENTS. Project guards return MEMBER_HAS_ASSIGNMENTS or PROJECT_NOT_EMPTY.
Bearer authentication and browser cookies follow the existing session rules; cookie
writes require CSRF.

Liquibase migration 004 adds tasks, execution attempts and same-project dependency
constraints. The React workspace exposes project task boards, filters, lifecycle
actions, dependency editing, attempt history, comments/labels and «Мои задачи».
Alpha import and resource reservations remain subsequent work. Alpha browser tasks
remain prototype data.

`GET /api/tasks/{id}/dependencies` returns predecessor IDs with `X-Project-Input-Revision`.
Project participants, owners and ADMIN edit incoming dependencies through
`POST /api/tasks/{id}/dependencies` with `{"dependsOnTaskId":123,"inputRevision":4}`
and `DELETE /api/tasks/{id}/dependencies/{dependsOnTaskId}?inputRevision=4`.
The successor must be NOT_STARTED with **no execution attempts**; FAILED and tasks
returned to NOT_STARTED by retry cannot change their dependencies. Predecessors can
have any status, but only COMPLETED predecessors satisfy readiness.

Both commands recheck access, state and the expected **project inputRevision** under
the common project lock. A successful change increments inputRevision once; task
metadata versions and execution history are unchanged. POST returns 201 and the new
revision; DELETE returns 204 and X-Project-Input-Revision. Stale graph input returns
409 VERSION_CONFLICT. Self-dependencies return 400 SELF_DEPENDENCY; cycles return
409 CYCLIC_DEPENDENCY, duplicates 409 DEPENDENCY_ALREADY_EXISTS, disallowed successor
states 409 DEPENDENCY_CHANGE_FORBIDDEN and missing edges 404 DEPENDENCY_NOT_FOUND.
A readable predecessor from another project returns 409 CROSS_PROJECT_DEPENDENCY;
missing/inaccessible referenced tasks remain indistinguishable as 404 TASK_NOT_FOUND.
Cookie writes require CSRF. Removing an edge can repair an oversized graph.

## Persisted project graph queries

Project members, owners and ADMIN can read a coherent graph snapshot through:

- `GET /api/projects/{id}/execution-order`: projectId, inputRevision and ordered taskIds.
- `GET /api/projects/{id}/available-tasks`: ordered NOT_STARTED tasks in bucket WORK whose
  actual predecessors are all COMPLETED; blockingReasons list blocked WORK candidates.
- `GET /api/projects/{id}/next-task`: the first available task, or 204 if none is available.
- `GET /api/projects/{id}/gantt`: working-time CPM bars (early/late start/finish, float,
  critical tasks/edges, deadlineOverrun vs task dueDate / project endDate). Calendar is
  Mon–Fri 09:00–17:00 with project break windows (default lunch 13:00–14:00 → 420 net
  minutes/day) in the project IANA timezone; missing estimates are zero-length
  milestones; resources are unlimited (no leveling). Overrun flags do not change criticality.

All four endpoints use the Java planning core, return `Cache-Control: no-store` and
`X-Project-Input-Revision` (also on 204), and never update execution facts or project revisions.

`GET /api/portfolio` returns a paged risk overview for accessible projects: `ON_TRACK`,
`AT_RISK` (any task deadline overrun) or `OVERDUE` (project end overrun), plus open/critical
task counts and planned duration. `GET /api/portfolio/workload` aggregates open assigned work
(open/estimated/overdue) across accessible projects. Workspace `#/overview` links each project
row to `#/projects/{id}` (WORK bucket when at risk) and each assignee row to
`#/tasks?assigneeId=` via `GET /api/tasks/assigned` (membership-scoped). CSV export on the
overview downloads only data already visible to the caller.

`POST /api/projects/{id}/gantt/what-if` accepts `{"estimates":[{"taskId":1,"estimatedMinutes":960}]}`
and returns a CPM snapshot with those overrides only in memory; persisted estimates are unchanged.
The snapshot loads under the same project lock as task writers; calculations run after
the read transaction releases the lock. FAILED/CANCELLED predecessors remain blockers.
Readiness does not check assignment, execution rights or resource calendars, and does not
reserve a task. A later execution command validates current facts again under its write lock.

`planning.graph.max-tasks` (default 10000) and `planning.graph.max-dependencies` (default
50000) bound snapshot loading. Oversized graphs return 409 GRAPH_TOO_LARGE rather than
calculating a partial graph. Queries use fixed bulk task/edge reads, without per-node loads.
These routes are included in `/v3/api-docs/tasks` when the alpha documentation profile is active.
Liquibase migration 005 adds a covering `(project_id, task_id, depends_on_task_id)` index
for bounded, ordered project-edge reads; previous migrations remain unchanged.

## In-app notifications

Task assignment, comments, lifecycle (start/complete/fail/cancel) and attachment uploads
enqueue durable `outbox_messages` rows (`task.assigned`, `comment.created`, `task.started`,
`task.completed`, `task.failed`, `task.cancelled`, `attachment.uploaded`) in the same write
transaction. `GET /api/notifications` drains a small outbox batch into per-user in-app
notifications, then returns a page with `unreadCount`.
`POST /api/notifications/{id}/read` and `POST /api/notifications/read-all` mark items read.
In-app channel uses Liquibase 010. Optional Telegram (013) and Email (014) are add-on channels
on the same outbox (`task.assigned`, `comment.created`, `task.completed`, `task.failed`).
Telegram: `TELEGRAM_BOT_TOKEN` / `TELEGRAM_BOT_USERNAME` / webhook secret / `APP_PUBLIC_BASE`;
bind via `POST /api/telegram/link` → `/start <token>`. Email: set `SPRING_MAIL_HOST` (optional
`SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD`) and `MAIL_FROM`; omit host to
disable. Shared `OutboxPushProjection` formats recipient + text for both channels (Strategy:
`TelegramBotClient` / `EmailSender`). `GET/PUT /api/email` status, mute, digest mode
(`IMMEDIATE` default or `DAILY` plain-text digest at 08:00 UTC via `mail.digest.cron`).
Workspace: `#/notifications`.

## Changelog (recent)

| Sprint | Delivered |
|--------|-----------|
| S43 | Timesheets / actual effort (`POST/GET/DELETE /api/time-entries`, Liquibase 018) |
| S42 | Per-project break windows (`GET/PUT /api/projects/{id}/breaks`, Liquibase 017) |
| S41 | Rich HTML email (multipart alternative + brand wrapper) |
| S40 | Portfolio estimate KPI (`plannedMinutes` / completion %) on overview |
| S39 | Hard resource leveling projection (`?resourceLeveling=true` on Gantt/what-if) |
| S38 | Dropbox refresh-token OAuth + local→Dropbox backfill (`POST /api/attachments/backfill`) |
| S37 | Email digests (`IMMEDIATE`/`DAILY`, Liquibase 016 queue + scheduler) |
| S36 | Dropbox `AttachmentObjectStore` Strategy (`ATTACHMENT_STORAGE`); Liquibase 015 `external_id` |
| S35 | Email outbox (Liquibase 014), prefs/mute, shared push projection; PR #66 → `dev` |
| S34 | DEMO/README polish |
| S33 | Soft resource contention on Gantt/workload |
| S32 | Fixed lunch 13:00–14:00 (`MINUTES_PER_DAY=420`) |
| S31 | Telegram outbox channel (Liquibase 013) |
| S30 | Notification UX + project holidays (012) |

Release batch S32–S34 → `main` (#65); S35 Email → `main` (#67); S37–S40 → `main`.

## Task activity history

`GET /api/tasks/{id}/activity` returns an append-only audit trail for a task (newest first,
paginated). Entries are written in the same transaction as create/update/lifecycle, comments,
label link/unlink, attachment upload/delete and dependency add/remove. Foreign or inaccessible
tasks return `TASK_NOT_FOUND`. Task deletion cascades activity rows (Liquibase 011). Workspace
task detail shows «История действий» next to execution attempts.

## Task attachments

Project members, owners and ADMIN can upload files through `POST /api/attachments?taskId={id}`
(`multipart/form-data`, field `file`). Limits for this release: max **5 MiB**; content types
`application/pdf`, `image/png`, `image/jpeg`, `image/webp`, `text/plain`. Metadata is stored in
PostgreSQL (Liquibase 009 + 015 `external_id`). Bytes use Strategy `AttachmentObjectStore`:
default `attachment.storage=local` (`attachment.storage-dir`); optional
`ATTACHMENT_STORAGE=dropbox` with refresh OAuth (`DROPBOX_APP_KEY` /
`DROPBOX_APP_SECRET` / `DROPBOX_REFRESH_TOKEN`) or `DROPBOX_ACCESS_TOKEN`, plus
`DROPBOX_APP_FOLDER` (default `/projectflow`). Upload writes Dropbox first; `external_id`
stores the file id. Dual store falls back to local disk for legacy rows.
ADMIN `POST /api/attachments/backfill?limit=` migrates local-only rows to Dropbox.

`GET /api/attachments?taskId=` lists newest first; `GET /api/attachments/{id}/content` downloads;
`DELETE /api/attachments/{id}` requires the uploader, current project owner or ADMIN with live
access. Uploads/deletes use the project write lock and do not change task or planning revisions.
Task deletion cascades attachment rows; Dropbox delete is best-effort. Workspace task detail
shows the attachment panel. Browser-cookie writes require CSRF.

## Project holidays

`GET /api/projects/{id}/holidays` returns the project's non-working weekday dates.
`PUT /api/projects/{id}/holidays` with `{"dates":["2026-11-04"],"version":N}` replaces the
full set (owner/ADMIN, optimistic `version`). Dates must lie within the project start/end
range; the write bumps `calendarRevision` so Gantt/CPM reload. Liquibase 012.
`WorkingCalendar` skips these dates in addition to Sat/Sun. Workspace project detail has a
«Праздничные дни» panel.

## Project break windows

`GET /api/projects/{id}/breaks` returns effective half-open windows inside 09:00–17:00
(default lunch `13:00–14:00` when none are stored).
`PUT /api/projects/{id}/breaks` with
`{"windows":[{"start":"12:00:00","end":"12:30:00"}],"version":N}` replaces stored windows
(owner/ADMIN); empty `windows` clears storage and restores the default lunch. Windows must
be non-overlapping, inside the working day, and leave positive net working minutes; the write
bumps `calendarRevision`. Liquibase 017. CPM/Gantt/portfolio use these windows via
`WorkingCalendar`.

## Timesheets (actual effort)

`POST /api/time-entries` with `{"taskId":1,"workDate":"2026-11-03","minutes":90,"note":"..."}`
logs actual minutes (1..1440) for the caller on an accessible task. Server sets `userId` and
UTC `createdAt`; planning revisions are unchanged.
`GET /api/time-entries?taskId=` lists newest first and returns `totalMinutes` across all pages.
`DELETE /api/time-entries/{id}` allows the author, project owner or ADMIN. Liquibase 018.
Portfolio completion % remains estimate-based.

## Deferred (explicit technical debt)

These items are **not** implemented; they need disproportionate product/ops work relative to
the current MVP slice and will land later when credentials, channel choice and capacity exist:

| Item | Why deferred |
|------|----------------|
| Dropbox per-user OAuth accounts | App-folder refresh token in env + ADMIN backfill; not end-user OAuth |
| Advanced email templates / i18n themes | Multipart HTML wrapper ships; no per-tenant themes |
| Full resource calendars / FTE capacity | Opt-in hard leveling serializes assignee overlaps; not multi-calendar FTE |
| Per-assignee break windows | Project-level breaks ship; assignee-specific calendars deferred with FTE |

Do not treat README assignment notes that mention per-user Dropbox OAuth as shipped.

## Comments and project labels

Project members, owners and ADMIN can create and list plain-text comments through
`POST /api/comments` with `{"taskId":123,"text":"Review this"}` and
`GET /api/comments?taskId=123`. The server supplies authorId and a UTC timestamp;
text is stripped and must contain 1..10000 characters. Markup remains plain text.
`DELETE /api/comments/{id}` requires current project access and the comment author,
current project owner or ADMIN. Author and timestamp fields cannot be supplied by clients.

`POST /api/labels` accepts `{"projectId":1,"name":"Bug","color":"#FF0000"}`.
`GET /api/labels?projectId=1` lists project labels; `GET /api/labels/{id}` reads one.
Names contain 1..100 characters after stripping the ends and are unique per project
using `Locale.ROOT` lowercase: `Bug` and ` bug ` conflict. Colors must be `#RRGGBB`,
case-insensitive on input and uppercase on output. Project participants can manage labels.
`PUT /api/labels/{id}` replaces name/color with an explicit expected `version`;
`DELETE /api/labels/{id}?version=0` also requires the current label version.
Stale versions return 409 VERSION_CONFLICT; duplicate names return 409 LABEL_ALREADY_EXISTS.

`PUT/DELETE /api/tasks/{taskId}/labels/{labelId}` idempotently adds/removes an association;
`GET /api/tasks/{taskId}/labels` lists assigned labels. Both parents must exist and belong
to the same project. A visible foreign-project label returns 409 CROSS_PROJECT_LABEL;
inaccessible labels and comments return 404 LABEL_NOT_FOUND/COMMENT_NOT_FOUND.
Lists use page 0..1000000, size 1..100 (defaults 0/20), newest ID first.
All responses use no-store; browser-cookie writes require CSRF.

Writes use the shared project lock and recheck current access after waiting.
Comments, labels and their associations do not change task versions or project/planning
revisions. Deleting an eligible task deletes its comments and label associations,
while preserving the project labels. Deleting a label removes its associations;
deleting an empty project removes its labels. Liquibase migrations 006/007 enforce
parent cleanup and same-project associations. The alpha profile documents these
routes separately at `/v3/api-docs/collaboration`; browser alpha storage remains separate.

## Text validation

Persisted free-text fields reject U+0000 with 400 VALIDATION_ERROR before the affected
text reaches SQL: project/task names and descriptions, profile first/last names,
comments and label names. Login identifiers follow the same rule for both Bearer
and browser-session login; password validation is unchanged.
The shared validation preserves Unicode, line breaks, optional
null values and the existing field-specific length/blank rules. Rejected updates
leave metadata, versions and planning revisions unchanged.

## Local development

The application uses Java 21, Spring Boot 4.1.1, Maven Wrapper and Node.js 24 for the React/TypeScript workspace build.
Dependencies: Spring Web MVC, Validation, Actuator, Spring Data JPA, Liquibase,
Spring Security OAuth2 Resource Server, and the PostgreSQL JDBC driver.

1. Install JDK 21 and set `JAVA_HOME` to its installation directory.
2. Open this directory as a Maven project in your IDE; select JDK 21 as the project SDK.
3. Build and run the tests in PowerShell:

   ```powershell
   npm.cmd ci --ignore-scripts
   .\mvnw.cmd clean verify
   ```

4. Configure the local database. Install and start Docker with Compose support.
   If Docker is available only inside WSL, run the Docker commands in WSL from
   `/mnt/c/Data/Projects/java/task-management-app`.
   If `.env` does not exist yet, copy the sample and replace its password:

   ```powershell
   Copy-Item .env.sample .env
   ```

   Start PostgreSQL and wait until it is ready:

   ```powershell
   docker compose up -d --wait
   ```

5. Configure a JWT signing key before starting the application. Generate it once,
   save it securely as `JWT_SIGNING_KEY_BASE64` in the ignored `.env` or environment,
   and retain it across restarts. No default key is provided. This example sets only
   the current process environment and does not print the key:

   ```powershell
   $keyBytes = New-Object byte[] 32
   $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
   try { $rng.GetBytes($keyBytes) } finally { $rng.Dispose() }
   $env:JWT_SIGNING_KEY_BASE64 = [Convert]::ToBase64String($keyBytes)
   ```

   Start the application from the project directory:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

6. Open `http://localhost:8080/actuator/health`; its `status` should be `UP`.
   Stop the application with `Ctrl+C`.

On Linux/macOS, use `./mvnw` instead of `.\mvnw.cmd`.
The first build requires internet access to download Maven and dependencies.
The application requires PostgreSQL when started normally. It reads `.env` as a
properties file; use simple `KEY=value` lines without shell `export` or quotes.
Environment variables take precedence. `.env` is ignored by Git; keep only
`.env.sample` in the repository.

PostgreSQL uses a persistent Docker volume and is exposed on localhost port 5432.
If the port is occupied, change `POSTGRES_PORT` in `.env`.
`docker compose down` stops the database and preserves its data.
Changing credentials in `.env` does not change users in an existing database volume.

## Identity API

The backend now persists users and sessions in PostgreSQL. The alpha UI continues
to store its projects/tasks in the browser; it does not use these user accounts yet.
Protected `/api/**` routes accept a Bearer JWT or a browser access cookie. POST register,
login and refresh remain public; browser session endpoints use the CSRF contract below.
Health and the explicitly enabled local alpha remain public. No permissive CORS policy is configured.

| Endpoint | Behavior |
|---|---|
| `POST /api/auth/register` | Create USER, never ADMIN; username, email, password, firstName, lastName |
| `POST /api/auth/login` | `identifier` accepts email or username, plus password; returns access/refresh tokens |
| `POST /api/auth/refresh` | `refreshToken` is consumed once; returns a new token pair |
| `POST /api/auth/logout` | Revoke current authenticated session, including its access token; 204 |
| `GET /api/users/me` | Current user's safe profile, including version |
| `PUT /api/users/me` | Replace username/email/firstName/lastName with expected version |
| `PATCH /api/users/me` | Partial update with expected version; omitted/null fields retain their values |
| `POST /api/users/me/password` | currentPassword/newPassword; revoke every session; 204 |
| `PUT /api/users/{id}/role` | Current ADMIN changes USER/ADMIN with expected version |

Username is 3–50 ASCII letters/digits/underscore/dot/hyphen; `@` is reserved for email
login. Username/email are trimmed and stored in lowercase (`Locale.ROOT`) with unique
database constraints; email is at most 254 characters. Names are trimmed, nonblank and
at most 100 characters. Passwords are 12–128 characters, are not trimmed, and are hashed
using Spring Security PBKDF2-HMAC-SHA256 with a random salt (310,000 iterations).
Passwords/hashes and ORM entities are never returned in user profiles.

JWTs use HS256, a configured key of at least 32 random bytes, issuer `projectflow`,
audience `projectflow-api`, and a 15-minute lifetime capped by session expiry.
Refresh sessions expire absolutely seven days after login; rotation does not extend
that deadline. Only SHA-256 hashes of 256-bit random refresh tokens are stored.
Reusing a consumed refresh token returns 401; it does not revoke the newly rotated token.
Each request checks the live session, password generation and current role in the database,
so logout/password changes and role demotion affect previously issued JWTs immediately.
Changing the signing key invalidates every access token; refresh sessions remain in the DB.
Token responses use `Cache-Control: no-store`; clients must protect tokens as credentials.

Invalid fields/JSON return 400, invalid credentials/session 401, forbidden role operations
403, missing user 404, duplicate username/email or stale version 409. Demoting the last
ADMIN returns 409 / `LAST_ADMIN`, including concurrent role changes. Errors use
`application/problem+json` with a stable `code`, without credential values.
Unknown JSON properties cannot assign role or another user's identity.

### Persistent browser session

The browser adapter uses the same persisted sessions and live authorization checks.
It sets `__Host-pf-access` and `__Host-pf-refresh` cookies with `Secure`, `HttpOnly`,
`SameSite=Strict`, `Path=/` and no Domain. Access lasts at most 15 minutes; the refresh
cookie persists until the original seven-day deadline. Tokens are never included in
browser session JSON or stored in localStorage/sessionStorage by this adapter.
External hosts require HTTPS. Secure is not disabled for ordinary HTTP; loopback
browser support is covered by the Chromium regression suite.

| Endpoint | Browser behavior |
|---|---|
| `GET /api/session/csrf` | Public bootstrap: masked token and headerName; HttpOnly CSRF cookie |
| `POST /api/session/login` | identifier/password plus CSRF; set auth cookies; return user/expiresAt |
| `POST /api/session/refresh` | CSRF plus refresh cookie; rotate once without extending expiry |
| `POST /api/session/logout` | CSRF plus optional refresh cookie; revoke and clear cookies; idempotent 204 |

Fetch `/api/session/csrf` before login, then send its token using its returned headerName
(`X-XSRF-TOKEN`). Fetch a new token after login or logout, which clear the old CSRF cookie.
Same-origin fetch sends the HttpOnly credentials automatically. Reloading the page can
read `/api/users/me`; after access expiry, refresh once and reload the required read.
Logout works even when access has expired. Password changes revoke all cookie and Bearer sessions.

Every browser session mutation and every cookie-authenticated API mutation requires CSRF;
missing/invalid tokens return 403 `CSRF_INVALID` without changing data. Explicit Authorization
headers take priority and never fall back to cookies. Bearer clients retain the existing
contract without CSRF, while `/api/session/**` mutations always require it.
These protections use Spring Security's [standard cookie CSRF repository and masked tokens](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

Coordinate refresh requests between tabs: only one use of a refresh token succeeds.
A rejected/replayed refresh returns 401 without deleting cookies, preserving a concurrent
winner's new credentials. Do not automatically replay a business write after a network error.
The workspace at `/app/` provides account forms, server project screens, project tasks
(with readiness queue and filters), task cards (lifecycle, dependencies, attempt history,
comments, labels) and «Мои задачи». The alpha UI
still uses its separate local projects and does not log in automatically.

### Browser workspace

After configuring PostgreSQL and the JWT key, run:

```powershell
npm.cmd ci --ignore-scripts
.\mvnw.cmd clean verify
java -jar target/task-management-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=workspace
```

Open `http://127.0.0.1:8080/app/`. The explicit `workspace` profile binds to loopback;
the normal startup serves neither workspace nor alpha assets. Maven performs strict
TypeScript checks and a Vite production build before packaging compiled assets in the JAR.
React/Vite are build dependencies; Node is not required by the packaged Java runtime.

The UI supports registration/login/logout, profile/password changes, paginated server
projects, creation/metadata edits/deletion of empty projects, membership and ownership.
Share the ID shown in your profile with a project owner; the current API adds members by ID.
Project and profile conflicts retain the draft and show the current version separately:
loading it does not silently replace your form or version. Session-expired drafts remain
only in the current tab's memory and can be resumed by the same account. Explicit logout,
password change or switching accounts clears the previous account's workspace.

Web Locks coordinate login/refresh/logout and authenticated requests across tabs. Each
operation rechecks the current account after obtaining the lock. Business writes are
never replayed automatically; an uncertain network result asks the user to inspect data
before explicitly retrying. Credentials remain in HttpOnly cookies, with CSRF required
for writes. Only theme preferences are stored in browser storage. Server long IDs/versions
use exact bigint parsing/encoding; dates remain ISO strings. Tasks and alpha-data import
are later milestones, and no user directory is exposed by this UI.

### First ADMIN

Public registration always creates USER. To bootstrap the first ADMIN, supply
`BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`,
`BOOTSTRAP_ADMIN_FIRST_NAME`, and `BOOTSTRAP_ADMIN_LAST_NAME` through the environment,
along with database settings and the signing key. Do not pass passwords in CLI arguments.
Run this separate command with the web server disabled:

```powershell
java -jar target/task-management-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=bootstrap --spring.main.web-application-type=none
```

The command closes its application context after creation. If an ADMIN already exists,
it fails with `ADMIN_ALREADY_EXISTS` instead of changing credentials or roles. A database
lock serializes concurrent bootstrap commands. Clear bootstrap credentials from the
environment afterwards. Later ADMINs are assigned by an authenticated ADMIN endpoint.

With the `alpha` profile, Swagger offers separate **alpha**, **identity**, **session** and **projects** definitions.
The specifications include `/v3/api-docs/identity`, `/v3/api-docs/session` and `/v3/api-docs/projects`;
task CRUD is not implemented.
Documentation stays disabled without the alpha profile.

### Protected projects and members

Projects are persisted in PostgreSQL and require a Bearer JWT or browser cookie. The creator becomes owner
and member in one transaction. Members can read the project; its owner and global ADMIN
can change metadata, manage members, transfer ownership and delete the currently empty
project. ADMIN has access to all projects. Other users receive the same 404 /
`PROJECT_NOT_FOUND` as for a missing project.

| Endpoint | Behavior |
|---|---|
| `POST /api/projects` | Create INITIATED project and owner membership (201) |
| `GET /api/projects` | List accessible projects, ID descending |
| `GET /api/projects/{id}` | Read accessible project |
| `PUT /api/projects/{id}` | Replace metadata with expected `version` |
| `PATCH /api/projects/{id}` | Patch metadata with expected `version`; null/omitted fields retain values |
| `GET /api/projects/{id}/members` | List member IDs and owner flags |
| `PUT /api/projects/{id}/members/{userId}` | Add existing user, JSON `{ "version": 0 }` |
| `DELETE /api/projects/{id}/members/{userId}?version=...` | Remove member; current owner cannot be removed |
| `PUT /api/projects/{id}/owner` | Transfer to existing member, JSON `{ "userId": 2, "version": 1 }` |
| `DELETE /api/projects/{id}?version=...` | Delete empty project through workflow (204); users remain |

Creation and PUT use `name` (nonblank, at most 200 characters after trimming),
`description` (at most 10000 characters, defaults to empty), required `startDate` and
`endDate` (ISO dates, end cannot precede start), and `timezone` (IANA ID, default `UTC`).
Both dates must be within PostgreSQL's finite DATE range, inclusive:
`-4713-11-24` through `+5874897-12-31` in ISO proleptic years (year zero is 1 BC).
POST, PUT and PATCH reject dates outside that range with HTTP 400 `VALIDATION_ERROR`
without changing the project or its revisions.
PUT also requires `version`; PATCH requires `version` and any metadata fields to change.
Use an empty string to clear the description in PATCH. Lists accept `page` 0..1000000
and `size` 1..100 (defaults 0 and 20), returning `content`, `page`, `size`, `totalElements`
and `totalPages`. Members are sorted by user ID descending.

Ownership transfer retains the previous owner as an ordinary member. Only the current
owner or ADMIN may perform later management operations. A current-version transfer to
the same owner is a no-op. Client-supplied owner, status and revision fields cannot
bypass these rules. Status changes are reserved for later execution commands.

All project writers acquire the same row lock before checking access and version;
current ADMIN rights are read from the database even within an existing transaction.
Metadata, membership and ownership changes increase `inputRevision` and `version`;
date/timezone changes also increase `calendarRevision`. `ProjectCoordination` exposes
the transactional lock and revision contract to future task/scheduling modules.

Invalid data returns 400 / `VALIDATION_ERROR`; a member attempting owner operations
receives 403 / `FORBIDDEN`. Missing membership returns 404 / `MEMBER_NOT_FOUND`;
a missing target user returns 404 / `USER_NOT_FOUND`. Version conflicts, duplicate
membership and removal of the current owner return 409 / `VERSION_CONFLICT`,
`MEMBER_ALREADY_EXISTS` and `OWNER_REMOVAL_FORBIDDEN`. Responses use `Cache-Control: no-store`.

Persisted tasks and files are not implemented yet. Deletion currently cascades only
membership rows. Future task/file deletion and assignment guards belong to workflow;
the alpha browser workspace remains separate from these projects and accounts.

### Database migrations and tests

Liquibase applies `src/main/resources/db/changelog/db.changelog-master.yaml` on
startup. The first migration creates the `task_management` schema for application
tables; Liquibase keeps its own tracking tables in `public`. Add each future
migration as a new file and include it in the master changelog. Do not edit migrations
that have already been applied to a shared database.

Hibernate uses `ddl-auto=validate`, so Liquibase manages table creation and changes.
The application and tests run in UTC, independently of the operating system timezone.
This also avoids JDBC startup failures with legacy timezone names such as `Europe/Kiev`.
The health endpoint also checks database connectivity without exposing credentials
or detailed diagnostics.

### Regression testing

Install browser dependencies with `npm ci --ignore-scripts`, then run `./mvnw clean verify`,
`npm run check:alpha` / `npm run test:alpha` and `npm run check:workspace` / `npm run test:workspace`.

For the development loop, use `./mvnw -Pfast-tests test` (Windows: `./mvnw.cmd -Pfast-tests test`).
It runs the JDK/domain/value/token/architecture and asset-adapter tests without starting Spring or databases.
Use `-Dtest=ClassName` for a focused regression. The fast profile is optional and is not a
release verification: default `clean verify` still runs all 126 tests, including HTTP,
authorization, migrations and concurrency, with the unchanged Checkstyle/JaCoCo gates.
CI runs the full suite with both H2 and PostgreSQL.
The planning tests include an independent exhaustive oracle: 80 seeded six-task DAGs
are compared against every valid permutation, and readiness is checked on 250 seeded
execution-fact sets. Lifecycle tests cover every state/command pair and repeated retries.
HTTP tests exercise malformed JSON/dates, null fields, safe integer and 1000-task boundaries,
graph diagnostics, unsupported methods/media types and OpenAPI profile isolation.
H2 databases use separate names per Spring test context; PostgreSQL verification overrides
the test URL as described below and checks the real database instead.

Browser storage tests reject impossible Gregorian dates without changing the stored record,
and ID allocation reports exhausted safe integer space rather than producing rounded IDs.
Normal static resource mappings are disabled, so Swagger WebJar files cannot bypass the
alpha profile; alpha's explicit asset whitelist and Swagger's profile handlers serve their assets.

For the browser suite, start PostgreSQL, build the JAR with `./mvnw clean verify`, then run:

```powershell
npm.cmd ci --ignore-scripts
npx.cmd playwright install chromium
npm.cmd run test:browser
```

Playwright starts and stops its own alpha/workspace server on loopback port 18082, uses isolated
browser storage, and leaves a separately running preview on port 8080 alone.
Set `ALPHA_TEST_PORT` to change the test port. To use an installed Google Chrome instead
of downloading Chromium, set `$env:PLAYWRIGHT_CHANNEL = 'chrome'` before running the suite.
The suite covers ordering/theme persistence, execution readiness, cycle rejection,
network/quota failures, stale storage caches, exhausted IDs, corrupt storage, XSS, Swagger
and selected WCAG A/AA checks across eight themes and List/Kanban views. A real cookie-session
scenario checks reload/browser restoration, HttpOnly credentials, CSRF-protected project writes,
refresh and database revocation across browsers. Workspace scenarios cover actual UI registration,
profile/password, two-browser projects and conflicts, membership/ownership/ACL, two-tab
refresh, draft restoration, logout, uncertain writes, removal and WCAG across eight themes.
Failure traces are written to `target/playwright-results`.
CI installs Chromium and runs this suite against PostgreSQL.

Test database cleanup and concurrent-call helpers live in test-only `support` classes.
Cleanup refuses non-test database names; concurrency helpers collect only explicitly
expected exceptions and propagate unexpected infrastructure failures.

### Coverage gate

`clean verify` instruments tests with JaCoCo and requires at least 60% line coverage
across all application Java classes, including the alpha adapter. There are no class
exclusions. Open `target/site/jacoco/index.html` for the HTML report; XML and CSV reports
are generated in the same directory. Both H2 and PostgreSQL CI builds enforce the gate.

To verify that the gate rejects insufficient coverage, temporarily demand 100%:
`./mvnw -Dcoverage.minimum=1.00 verify`. A coverage violation must fail the build;
run the normal `clean verify` afterward. Do not use the override to lower the shared threshold.
The plugin configuration follows the [JaCoCo check documentation](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html).

### Architecture gate

ArchUnit runs with the normal tests and imports application classes only. It enforces
JDK-only dependencies for `planning.core` and `task.domain`, package acyclicity, and
allowed intermodule dependencies through public `api` packages. The graph core is the
explicit exception: callers use `planning.core`. Root application/config classes may
wire modules; business modules cannot depend back on bootstrap or other modules' internals.
The alpha adapter may call only the planning module and shared contracts.
The tests use the core [ArchUnit API](https://www.archunit.org/userguide/html/000_Index.html)
with the existing JUnit runner, without another test engine.

### Application container

Start the application and PostgreSQL together:

```powershell
docker compose --profile app up --build -d --wait
```

Open `http://localhost:8080/actuator/health`. `APP_PORT` in `.env` changes the host
application port; PostgreSQL remains reachable inside Compose as `postgres:5432`
even when its host `POSTGRES_PORT` changes. Starting Compose without the `app` profile
starts only PostgreSQL, for running Java from the IDE. Stop just the application with
`docker compose --profile app stop app`, or the stack with `docker compose --profile app down`.
Neither command removes the database volume.

By default, the Dockerfile checks and builds the workspace in a Node 24 stage, then runs
Maven Wrapper `clean verify` in a Java 21 JDK stage using those exact frontend assets and
copies only the application JAR into a JRE runtime stage. The Node/Temurin bases are pinned
by digest; update their digests deliberately and repeat the checks when upgrading.
The runtime runs as UID 10001 in UTC, checks Actuator health, and starts after the
database becomes healthy. Both host ports bind to loopback. The alpha profile is off.
The build context allowlist excludes `.env`, local docs, Git, session files and host
build outputs except the exact application JAR; database credentials are supplied only at container startup.
This uses Docker's [multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
and [healthy dependency ordering](https://docs.docker.com/compose/how-tos/startup-order/).

Run the repeatable container check from PowerShell (or `pwsh` on Linux/macOS):

```powershell
./scripts/verify-container.ps1
```

It creates an isolated Compose project with disposable PostgreSQL storage on ports
18081/15432, builds the image, checks health, alpha isolation, UID/UTC and migration
history, verifies ADMIN bootstrap/login and missing-key rejection, and restarts the application.
It removes only that check's containers,
network and volume afterward; the development database is untouched. Use `-AppPort`
and `-DatabasePort` if those check ports are occupied. CI runs the same script.
Build/restart logs are stored under `target/container-check/`.

CI packages the JAR produced by the immediately preceding successful full H2/PostgreSQL
verification instead of recompiling and rerunning the same tests inside Docker. The
runtime stage and all container startup/security/migration/restart checks are shared
with the default source-build path. On a clean checkout, the optional mode requires
both the tested commit and the SHA-256 of that JAR; it checks them before building and
checks the JAR hash again inside the running container. The caller must capture these
values immediately after successful full verification of the same unchanged checkout:

```powershell
./mvnw.cmd clean verify
if ($LASTEXITCODE -ne 0) { throw 'Full verification failed.' }
$testedRevision = (git rev-parse HEAD).Trim()
$testedJarHash = (Get-FileHash target/task-management-app-0.0.1-SNAPSHOT.jar -Algorithm SHA256).Hash
./scripts/verify-container.ps1 -VerifiedRevision $testedRevision -VerifiedJarSha256 $testedJarHash
```

Default `docker compose build` and `verify-container.ps1` still build and verify from
source. Newer CI commits cancel obsolete runs of the same event/ref; push and proposed-merge
checks remain independent and both are required for the sprint workflow.

Local tests use an in-memory H2 database in PostgreSQL compatibility mode and do
not require Docker. The startup test verifies that Liquibase created the schema
and recorded the migration. This does not cover all PostgreSQL-specific behavior.
CI runs separate H2 and PostgreSQL 17 builds using `SPRING_DATASOURCE_*` environment
variables to override the test datasource. It also starts the packaged application
twice and checks health, exercising Liquibase's existing migration history on restart.

To run the tests against your local PostgreSQL from PowerShell, with the database running:

```powershell
./scripts/verify-postgresql.ps1
```

The script creates a disposable `task_management_test_<random>` database in the local
Compose PostgreSQL, runs `clean verify`, and removes only that database afterwards.
Host/port/credentials resolve from `.env`. It never runs fixture cleanup in the development
database. Identity tests reject database names other than `task_management_test_*` or
the dedicated CI database `task_management_ci` before deleting any fixtures.

### Code style and CI

If IntelliJ IDEA Maven import fails with
`No implementation for ArtifactHandlerManager was bound`, select **Bundled (Maven 3)**
in **Settings → Build, Execution, Deployment → Build Tools → Maven**, disable
**Work offline**, and reload all Maven projects. This was verified with IntelliJ IDEA
Community 2025.2.6.2 and its bundled Maven 3.9.9. Command-line and CI builds use the
project Maven Wrapper.

`clean verify` checks the style of production and test Java sources, runs tests,
and packages the application. A style violation fails the build before compilation.
The shared rules are in `config/checkstyle/checkstyle.xml`: four spaces for indentation,
120 characters per line, no tabs or trailing whitespace, and no wildcard or unused imports.
`.editorconfig` supplies matching indentation settings for editors that support it.

To run only the style check in PowerShell:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress validate
```

GitHub Actions runs the same Maven Wrapper build on pushes and pull requests,
using Temurin Java 21 and a Maven dependency cache. It can also be started manually
from the Actions tab. The workflow is in `.github/workflows/ci.yml`.
After pushing these files to GitHub, inspect the CI run in the Actions tab.
To require a successful build before merging, add `Build, tests and Checkstyle`
as a required status check in the protection settings for the base branch.

The original assignment requirements follow below.

### Local alpha interface

With the alpha profile enabled, open `http://localhost:8080/swagger-ui/index.html`
for interactive API documentation or `/v3/api-docs` for the generated OpenAPI JSON.
The default **alpha** definition documents `POST /alpha/plan`; choose **identity**
for registration, sessions, profiles and roles, or **projects** for persisted projects,
members and ownership, or **tasks** for persisted task metadata and execution history.
Swagger and its specification are disabled without alpha, including normal container startup.
The [springdoc 3.1.1 release](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.1.1)
is pinned and verified with this project's Boot 4.1.1 / Java 21.

HTTP errors use `application/problem+json` with `type`, `title`, `status`, `detail`,
`instance` and stable `code`. Graph errors additionally retain `taskIds` for the alpha
client. Malformed JSON/types/dates produce `MALFORMED_REQUEST`; bean validation produces
`VALIDATION_ERROR`; cycles produce HTTP 409 / `CYCLIC_DEPENDENCY`. Cycle IDs can include
blocked descendants and do not represent only a cycle witness. Internal parser details
and rejected field values are not included in validation responses.

The early application interface is available at `http://localhost:8080/alpha/`
when explicitly started with the `alpha` profile. It provides projects, task creation
and editing, priorities, minute estimates, due dates, dependencies, filters, and
start/complete/fail/retry actions for the prototype.

New tasks appear at the top by default. The task-order selector supports newest first,
oldest first and the execution order returned by the Java core. Display sorting leaves
the stored task creation order and dependency calculation unchanged. Dark theme is the
default; the upper-right toolbar groups List/Kanban, the theme shortcut and settings.
Gray uses charcoal surfaces (RGB channels at most 40%) and beige/ivory foregrounds. Open
**Interface settings** to choose the theme, List/Kanban view and comfortable/compact
density. All preferences persist in this browser separately from project/task data;
existing preference records retain their theme/order and use defaults for new fields.
Eight palettes are available: Dark, Light, Gray, Ivory, Pearl, Graphite, Taupe and Forest.
The theme shortcut cycles through all eight; the settings dialog displays palette swatches.

The project overview displays total, ready, active and completed task counts plus completion
progress. Search filters task names locally in both views. The Kanban board groups the existing
prototype task states; use the card actions to change state with the same dependency guards.
Search, display settings and sorting never rewrite workspace data or recalculate the graph.

Fresh browsers include three business demo projects alongside the introductory project:
client portal (parallel dependencies and all four states), service recovery (retry and
blocked successors), and priority queue (priority, due date and ID tie-breaks).
Choose **Открыть демо-сценарии** to add missing scenarios to an existing workspace
and open the client portal. Loading them again preserves edits and avoids duplicates.
Each demo project includes a presentation guide. All graph results come from `/alpha/plan`;
demo data and prototype statuses stay in browser storage. Dates are illustrative;
estimates do not yet calculate schedules, Gantt or the critical path.
See [the customer demo walkthrough](DEMO.md) for presentation steps and the separate
identity/project API demonstration.
See [the product development roadmap](PRODUCT_ROADMAP.md) for the proposed path from
this local prototype to server-backed project work, task execution and scheduling.

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=alpha'
```

Or after building:

```powershell
java -jar target/task-management-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=alpha
```

Start the PostgreSQL service as described above. The alpha server binds to loopback.
Projects and task states are stored only in this browser (IndexedDB with a localStorage
compatibility mirror); clearing site
data removes them. The server calculates execution order and readiness using the real
JDK core and rejects cyclic changes before the browser saves them. It does not store
these prototype projects/tasks in PostgreSQL or connect this UI to user accounts.
The separate protected project API is persisted; task CRUD, persisted execution history,
calendars and resource guards remain planned features. This
prototype does not replace their acceptance criteria.

Saving requires IndexedDB and Web Locks in a secure browser context (loopback qualifies).
Existing localStorage workspaces are imported without removing the original record.
Workspace writes check the expected revision and update the canonical workspace in one
IndexedDB transaction under an exclusive lock. This avoids stale localStorage renderer
caches letting two tabs overwrite each other. A stale tab
reports a conflict and preserves the saved data and open form input; reload it to use the
latest workspace. Selecting the current project does not write workspace data. Existing
records without a revision are upgraded on their first successful save. Unavailable IndexedDB
disables saving while retaining readable legacy data. Reload alpha pages opened before this
update before editing, so all tabs use the canonical revision protocol.

The page, its assets and `/alpha/plan` are disabled without the `alpha` profile. No
local docs or secrets are served. Frontend assets need no runtime CDN or bundler.
With Node 24 installed, run the frontend verification separately:

```powershell
npm.cmd ci --ignore-scripts
npm.cmd run check:alpha
npm.cmd run test:alpha
```

### Pure planning core

`com.example.taskmanagement.planning.core` is a JDK-only algorithm package, introduced
before domain CRUD. It provides immutable `TaskNode`, `DependencyEdge`, `GraphSnapshot`
and `ExecutionFacts`, priority-aware topological ordering, and dependency readiness.
Task adapters will map CRITICAL > HIGH > MEDIUM > LOW to explicit numeric weights.
Optional estimates are positive whole minutes, allowing fractional hours without
floating-point arithmetic. Estimates are not needed for ordering or readiness.

The sorter uses Kahn's algorithm with a priority queue: higher weight first, then
earlier due date (missing dates last), then smaller ID. Dependencies always win over
successor priority. Runtime is O(E + V log V), with O(V + E) graph storage. Snapshot
construction checks structural errors; the sorter rejects cycles with a typed
`CYCLIC_DEPENDENCY` diagnostic. Its unresolved IDs can include descendants of a cycle.

Execution order includes every task and does not simulate real completion. Available
tasks are only supplied candidates whose predecessors are actually completed; next
is a recommendation, without starting a task or reserving an assignee. Resource and
permission guards, calendars, CPM and domain REST endpoints follow in later sprints.
Both readiness methods validate the entire graph and execution facts before selecting
candidates. `nextTask` selects the minimum using the same priority comparator without
sorting or materialising all available candidates; complete DAG validation still costs
O(E + V log V).

After building, try the pure core with `jshell --class-path target/classes`:

```java
import com.example.taskmanagement.planning.core.*;
import java.util.*;

var a = new TaskNode(1, 10, null, 30L);
var b = new TaskNode(2, 40, null, 60L);
var graph = GraphSnapshot.of(List.of(a, b), List.of(new DependencyEdge(1, 2)));
var sorter = new PriorityTopologicalSorter();
var readiness = new ReadinessCalculator(sorter);
sorter.executionOrder(graph).stream().map(TaskNode::id).toList(); // [1, 2]
readiness.availableTasks(graph, new ExecutionFacts(Set.of(), Set.of(1L, 2L)))
    .stream().map(TaskNode::id).toList(); // [1]
readiness.availableTasks(graph, new ExecutionFacts(Set.of(1L), Set.of(2L)))
    .stream().map(TaskNode::id).toList(); // [2]
```

Run only the core tests in PowerShell, without starting a Spring context or database:

```powershell
.\mvnw.cmd '-Dtest=GraphSnapshotTest,PriorityTopologicalSorterTest,ReadinessCalculatorTest' test
```

### Contribution process

This project is developed by one developer with an AI agent. The selected workflow
supersedes the original assignment's direct feature-to-main instructions below:
create `sprint/sNN` from `dev`, make small commits, review and test the sprint PR into
`dev`, then repeat tests on the merged `dev` commit before a release PR into `main`.
Use merge commits, check main CI/health, and synchronise `dev` before the next sprint.
Keep only `main`, `dev` and the current sprint branch locally and on origin; delete
integrated branches and their clean temporary worktrees before starting the next sprint.
Required checks are Maven Wrapper `clean verify`, current GitHub CI, and applicable
database/startup tests. Local planning documents and secrets are excluded from Git.

Manage tasks and projects effectively through a web-based application. This system enables task creation, assignment, progress tracking, and completion.


## If you work in a team

1. Create an organization on GitHub
   ![Create an organization](./description/create-an-organization.png)
2. Choose a `Free` plan
3. Use a name like `fe-feb20-team0` (your group + your team name)
4. It should belong to your personal account
   ![Set up your team](./description/set-up-your-team.png)
5. Add your teammates to the team by their usernames on Github
   ![Add your teammate](./description/add-your-teammate.png)

## Instructions

1. Create a new Spring Boot project
2. Create a new GitHub repo with `task-management-app` name (or any other you like)
    - if you work in a team the repo MUST belong to the organization;
    - configure `Branch protection rules` for the organization repository using the following tutorial:

      <details>
        <summary>Tutorial for branch protection rules</summary>

      Go to the repository settings
      STEP #1
      ![Add your teammate](./description/first-step-for-branch-protection-rules.png)

      STEP #2
      ![Add your teammate](./description/second-step-for-branch-protection-rules.png)

      Add the rule with only the next settings
      STEP #3
      ![Add your teammate](./description/third-step-for-branch-protection-rules.png)

      More information about all other settings at
      the [link](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/defining-the-mergeability-of-pull-requests/managing-a-branch-protection-rule)
      </details>

3. Connect a repo to a folder with your Spring Boot project (see the instructions on GitHub);
4. Setup checkstyle plugin in your project
5. Setup CI process to run the `mvn clean verify` command by adding a `.github/workflows/ci.yml` file
6. The `master` (or `main`) branch is a base for your feature PullRequests.
7. PRs should be merged thereafter review

## How to develop a feature

1. `Pull` the latest `master`.
2. Create a `your-feature-name` branch and `push` it to GitHub.
3. Open a Pull Request (`PR`) from your branch to the `master` (`main`).
4. Discuss branches and commit naming practices. Your branch, PR, and commit names should be consistent across the
   team. Example of PR names:
    - [DB]: prefix for liquibase scripts
    - [API] for scripts with API changes
5. Do not use forks, work in a single repo altogether!
6. Write your code, save it, and push it to GitHub.
7. Ask your teammate to review and approve if everything is OK.
8. If some fixes are required discuss the comments and repeat steps 5-7.
9. Mentors will review your codebase every day, but not PRs.

## Coding

- Add checkstyle plugin.
- Separate PR to the `main` branch for each task is required.
- 60%+ of the custom code should be covered with tests.
- Make sure to name your commits & branches meaningfully.
- Do not use forks, work in a single repo altogether (if you work in a team).

## App

### Requirements:

- Functional (what the system should do):
    - Web-based
    - User registration and authentication
    - Project management (CRUD operations)
    - Task management within projects
    - Assign and notify users about tasks
    - Add comments and attachments to tasks
    - Organize tasks with labels
    - Store attachments on Dropbox using its API
- Non-functional (what the system should deal with):
    - Scalable to handle a large number of users
    - Efficient retrieval of tasks, comments, and attachments
    - Secure storage of sensitive user data

### Models

1. **User**:
    - Username: String
    - Password: String
    - Email: String
    - First Name: String
    - Last Name: String

2. **Project**:
    - Name: String
    - Description: String
    - Start Date: LocalDate
    - End Date: LocalDate
    - Status: Enum: [INITIATED, IN_PROGRESS, COMPLETED]

3. **Task**:
    - Name: String
    - Description: String
    - Priority: Enum: [LOW, MEDIUM, HIGH]
    - Status: Enum: [NOT_STARTED, IN_PROGRESS, COMPLETED]
    - Due Date: LocalDate
    - Project ID: Long
    - Assignee ID: Long

4. **Comment**:
    - Task ID: Long
    - User ID: Long
    - Text: String
    - Timestamp: LocalDateTime

5. **Attachment**:
    - Task ID: Long
    - Dropbox File ID: String   // Store the reference to Dropbox
    - Filename: String
    - Upload Date: LocalDateTime

6. **Label**:
    - Name: String
    - Color: String

### Controllers

1. **Auth Controller**:
    - POST: /api/auth/register - User registration
    - POST: /api/auth/login - User authentication

2. Users Controller: Managing authentication and user registration
    - PUT: /users/{id}/role - update user role
    - GET: /users/me - get my profile info
    - PUT/PATCH: /users/me - update profile info

3. **Project Controller**:
    - POST: /api/projects - Create a new project
    - GET: /api/projects - Retrieve user's projects
    - GET: /api/projects/{id} - Retrieve project details
    - PUT: /api/projects/{id} - Update project
    - DELETE: /api/projects/{id} - Delete project

4. **Task Controller**:
    - POST: /api/tasks - Create a new task
    - GET: /api/tasks - Retrieve tasks for a project
    - GET: /api/tasks/{id} - Retrieve task details
    - PUT: /api/tasks/{id} - Update task
    - DELETE: /api/tasks/{id} - Delete task

5. **Comment Controller**:
    - POST: /api/comments - Add a comment to a task
    - GET: /api/comments?taskId={taskId} - Retrieve comments for a task

6. **Attachment Controller**:  // This is where we will interact with Dropbox API
    - POST: /api/attachments - Upload an attachment to a task (File gets uploaded to Dropbox and we store the Dropbox File ID in our database)
    - GET: /api/attachments?taskId={taskId} - Retrieve attachments for a task (Get the Dropbox File ID from the database and retrieve the actual file from Dropbox)

7. **Label Controller**:
    - POST: /api/labels - Create a new label
    - GET: /api/labels - Retrieve labels
    - PUT: /api/labels/{id} - Update label
    - DELETE: /api/labels/{id} - Delete label

### Dropbox Integration:

To store the attachments of tasks, we will use the Dropbox API. The general flow is:

1. When a user uploads an attachment, our backend will upload this file to Dropbox.
2. Dropbox returns a unique File ID after a successful upload.
3. We store this Dropbox File ID in our database against the attachment details.
4. When someone wants to retrieve/download the attachment, we use this File ID to fetch the file from Dropbox and serve it to the user.

This approach allows us to leverage Dropbox's robust file storage system, keeping our system lightweight and focused on task management.

Read more about [Dropbox API](./dropbox.md)

## Tasks

### Infrastructure

- Set up a task management board on a platform like Trello or Jira to keep track of progress.
- Initialize a new repository on GitHub or another platform for source code version control.
- Set up a CI/CD process to automate testing and deployment.
- Integrate a database migration tool, e.g., Liquibase or Flyway, to handle database schema changes.
- Add a health check endpoint to ensure the application is running and connected to the database.
- Containerize the application using Docker to ensure a consistent runtime environment.
- Store sensitive information such as API keys in a `.env` file, and add a `.env.sample` with placeholders to the repository.
- Implement API documentation using tools like Swagger to provide an interactive interface for testing and understanding the API.
- Update the `README.md` with setup instructions, API endpoints, and other relevant information once the project is complete.

### User Management

- Implement user registration and authentication mechanisms.
- Add user roles and permissions (e.g., ADMIN, USER) to control access to certain functionalities.
- Secure API endpoints using JWT tokens or other security mechanisms.

### Project Management

- Set up database tables and JPA entities for projects.
- Implement CRUD operations for projects.
- Ensure only authorized users can access, modify, or delete projects.

### Task Management

- Create tables and entities for tasks.
- Implement CRUD operations for tasks.
- Allow tasks to be assigned to users and notify assignees of new tasks.

### Comments

- Set up tables and entities for comments.
- Allow users to add, view, and delete comments related to tasks.

### Attachments

- Design the attachment entity to store metadata about files (filename, upload date, etc.), but the actual files will be stored on Dropbox.
- Integrate with the Dropbox API to upload, retrieve, and delete files.
    - When a new attachment is uploaded, save it to Dropbox and store the returned metadata in the application database.
    - For retrieval, fetch the metadata from the database and use the Dropbox API to fetch the actual file.
    - Deleting an attachment should remove the metadata from the database and also delete the file from Dropbox.
- Ensure only authorized users can upload, view, or delete attachments.

### Labels

- Implement database tables and entities for labels.
- Allow users to create, modify, and delete labels.
- Allow labels to be assigned to tasks for better organization.

### Notifications
- Integrate a notification system, like email or Telegram, to notify users about task deadlines, comments, and other updates.

### Optional:
- Deploy your app to the AWS

### Advanced
- Implement a search and filter mechanism for tasks and projects.
- Integrate with third-party applications, like Google Calendar, to provide additional functionalities.
