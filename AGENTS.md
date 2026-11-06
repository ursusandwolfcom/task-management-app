# Course submission working rules

Communicate in Russian. Maintain documentation in English. One developer and one
AI agent; do not spawn sub-agents without an explicit user request.

## Authorized branch exception

The user requested an independent, simplified assignment version on 2026-11-06.
`course/submission` branches from preserved `sprint/s56` commit `869c63d`.
Keep `main`, `dev` and `sprint/s56` intact. This extra branch is an explicit exception
to the original three-branch sprint rule. Do not merge this reduced application
into the extended product's dev/main. Do not close its existing PR or delete its
sprint branch as part of course simplification. The normal sprint/release workflow
continues to apply to that product, not to this independent submission branch.

## Scope

Implement the original Mate task-management assignment: six models, seven REST
controller areas, roles/JWT, project/task CRUD, membership and assignment, comments,
labels, Dropbox upload/download/delete, simple search/filters and email notifications. Keep the conventional
controller/service/repository/model/dto/mapper structure. No frontend, DAG/CPM,
calendars, capacity, time tracking, MEGA, Telegram, outbox or generic module ports.
Optional advanced features must not be added without a new request.

MapStruct is a mandatory course technology, explicitly confirmed by the user. Keep
annotation processing enabled and use generated Spring mapper implementations with
unmapped target errors; do not replace them with handwritten response constructors.

The user explicitly selected a new empty database, email only, and reminders at
09:00 UTC on the previous/due day to the assignee. Keep the old application's data
and secrets untouched. This branch imports only ignored `.env.course`; never print
or commit `.env` or `.env.course`. Use a separate course database and volume.

Preserve Java 21, UTC, Liquibase tracking in public and ddl-auto=validate. Keep
BCrypt, authorization, validation, pagination and meaningful tests. Run Maven Wrapper
clean verify, maintain the 60% line coverage gate and Checkstyle, and require green
PostgreSQL/container CI on the exact published SHA. Never bypass branch protection,
force-push shared history or call mocks live-provider acceptance.

Preserve untracked/ignored user files. Local docs stay ignored; update
docs/CHANGELOG.md and docs/CHANGELOG_UML.puml with matching iteration IDs and honest
validation status. Public README must not link ignored local documentation.
Use relevant installed requirements, Java architecture, implementation, review and
debugging skills. Ask about consequential unresolved business rules; continue work
that does not depend on the answer. New repository creation/deployment is separate.
