# ProjectFlow: from demonstration to a working business system

Proposed on 2026-10-13 at the user's request. This describes implementation priorities,
not a list of completed features. It does not assign dates or new sprint numbers.
The initial snapshot below is historical; consult README and Git/PR history for current delivery status.

Product value: users see what to do next, who owns the work, what blocks the project
and how a task change affects deadlines. The main screen is the project/task workspace.
The overview helps users decide and act: every metric should open its underlying tasks or projects.

## Initial working baseline

| Area | Implemented at the initial planning baseline | Boundary at that baseline |
|---|---|---|
| Planner | Java planning.core: DAG order, priority → due date → ID, readiness/next, cycle rejection | Alpha submits a graph snapshot; date/resource scheduling is not yet present |
| Users | Registration/login, profile, password and logout API/UI; persistent cookie session with CSRF | Global roles use the ADMIN API; no user-directory UI |
| Projects | Protected PostgreSQL CRUD and directory/detail UI, membership, ownership transfer, access/version checks | Alpha projects are separate; server tasks are not yet connected |
| Execution | Pure domain lifecycle and execution attempts covered by tests | Server task CRUD and history UI are not yet connected |
| Alpha interface | Browser projects/tasks, list/Kanban, search/filters, dependencies, actions, metrics, eight themes | Local prototype; no collaboration between accounts |
| Demo | Three scenarios / 21 tasks with actual server calculation | Illustrative dates and local statuses |

Later sprints added persisted tasks, collaboration, planning, portfolio, notifications,
resource calendars and file integrations. The initial limitations above are retained
as historical context rather than current defects.

## Priorities and verifiable outcomes

Three paths were considered: expand the local prototype (fast visual feedback without
collaboration); connect existing APIs and incrementally complete server tasks (recommended);
or start with reports/integrations (more external dependencies before the working loop is complete).
Keep the modular monolith and independent planning.core; this plan does not require a new stack.

| Stage | Implementation scope | Value and acceptance criterion |
|---|---|---|
| M1. Account and server projects | Login/register/logout, profile, project list/detail, membership/ownership through existing APIs; session expiry and version conflict handling | One account sees the same projects in two browsers. Outsiders cannot read/change another project. UI explains denials and preserves form input |
| M2. Server tasks | PostgreSQL task CRUD, team-member assignee, priority/deadline/estimate, start/complete/fail/cancel/retry, attempt history; task forms/detail | Tasks survive browser changes. Server rejects invalid transitions. Retry preserves the previous attempt. Concurrent edits/actions do not overwrite each other |
| M3. Dependencies and work queue | Persisted DAG with transactional graph/state checks, availability/next API; My tasks, explained blocks, server-backed list/Kanban | A → B: completing A unblocks B; cycles and cross-project edges are rejected. Concurrent start/edit cannot bypass checks. Users find and execute ready work |
| M4. Collaboration | Comments, labels, assignee/status/deadline/label filters, pagination, activity history; Dropbox attachments and chosen notification channel | Discussion/documents remain in task context and available only to members. External failures do not report false success; repeated delivery/upload does not duplicate operations |
| M5. Planning | DAG/calendar dates, Gantt, cross-project assignee availability, CPM and non-persistent what-if | Plans respect dependencies, working time and assignee occupancy. Estimate changes show a new finish, critical branch and deadline warnings |
| M6. Management overview | Portfolio, risk/overdue indicators, workload, drill-down to tasks; access-controlled report export | A manager opens the cause of a delay and assigns an action. Metrics use persisted data; exports include only accessible information |

M1 can start on the existing backend. M2 → M3 deliver the central complete scenario.
M4 collaboration and M5 planning follow reliable task/dependency persistence. M6 depends
on their data. Add the working-list UI with each stage's API rather than postponing it
until a separate dashboard exists.

## Intended product experience

- **Projects:** working directory, description, members, risks and available actions.
- **My tasks:** personal queue with readiness, deadlines and reasons for blocks.
- **Project workspace:** list/Kanban, filters, next action; Gantt later.
- **Task card:** details, assignee, dependencies, actions, history; discussion/files later.
- **Team and project settings:** membership, permissions and ownership; work calendars later.
- **Portfolio overview:** metrics leading into work, without decorative static numbers.
- **Profile and interface preferences:** theme, density, accessibility; notifications later.

These describe the original progression; later delivery is recorded in README.
Navigation entries appear with working scenarios. Projects, filters and task cards
have direct links and restore context. Loading, empty lists, access denial, conflicts
and network failures have clear states and recovery actions.

## Complete MVP acceptance scenario

1. The owner signs in, creates a project and adds a member.
2. Creates two server tasks A → B and assigns a team member.
3. The member opens their queue: B is blocked with an explanation, A is ready.
4. Completes A; B becomes ready. Failing B and retrying preserves attempt history.
5. A second browser shows the same saved state; an outsider has no access.
6. A cyclic graph change or version conflict produces a clear denial without losing form input.

This scenario requires M1–M3. Gantt, files and notifications are not prerequisites.

## Decisions for the relevant implementation stage

### MEGA attachment integration (mega.nz)

Added at the user's request on 2026-11-04. Later that session, the user requested
implementation and selected personal accounts, the official native SDK and unlinking
with a warning. S52 implementation and instructions: [INTEGRATIONS.md](INTEGRATIONS.md).
Existing attachment providers remain available; real-account acceptance is required.

- Goal: upload, download and delete task files through MEGA, retaining project access
  checks and PostgreSQL metadata.
- Extension point: `AttachmentObjectStore`; transport and cryptography stay outside task business logic.
- Personal accounts are selected; sessions are encrypted and passwords are not persisted.
  Unlink retains metadata; old files require reconnecting the original MEGA account.
- Review the official [MEGA SDK](https://github.com/meganz/sdk), Java 21/container
  integration and license terms before choosing a dependency. S52 pins SDK 10.22.0.
- Acceptance: a member uploads and retrieves identical content; an outsider cannot
  retrieve it; MEGA failure does not report false success or leave inconsistent metadata;
  repeated operations/deletion have contract and integration coverage.
- Automatic migration from existing stores requires a separate decision.

### Other decisions

- Resolve any remaining task edit/execute permissions, mandatory assignee rules and
  permitted IN_PROGRESS changes against the applicable requirements.
- Persistent browser login uses Secure HttpOnly cookies with CSRF over JWT/DB sessions.
  Cookie APIs and workspace login/restore/refresh are implemented, including cross-tab
  coordination and form preservation on conflicts. Secrets do not enter URLs or demos.
- Decide whether explicit import of local alpha projects into an account is needed.
  Never automatically mix local and PostgreSQL IDs; demo remains a separate mode.
- Calendars: working hours, days off, timezone and estimate rounding rules.
- Notifications: channels/events; files: size, type and access limits.
- Metrics: overdue, risk and workload definitions. Duration estimates alone are not
  actual effort and do not justify financial KPIs.

Every stage requires current contracts/README, PostgreSQL integration checks,
access/concurrency tests, a browser scenario with the real API and accessibility checks.
Integration follows sprint → dev → main with checks on the exact current SHA.
