# Task Management API

A course REST API for projects, tasks, comments, labels and Dropbox attachments.
Users authenticate with JWT; project owners manage membership and tasks can be
assigned to project participants. Email covers assignments, comments and deadlines.

This is the independent `course/submission` version. The extended ProjectFlow
application is preserved on `sprint/s56` at `869c63d`. This version requires a **new,
empty database** and does not migrate ProjectFlow data.

## Stack

Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA, PostgreSQL 17,
Liquibase, Spring Mail, Dropbox SDK and springdoc OpenAPI. Maven Wrapper runs
Checkstyle, JUnit integration tests and a JaCoCo line coverage gate of 60%.
There is no frontend or Node build step.

## Run locally

Prerequisites: Java 21 and Docker Compose.

1. Copy `.env.sample` to `.env.course`. Set a database password, a random JWT key
   and a Dropbox access token. `.env.course` is ignored. The existing `.env` is
   deliberately not imported by the application.
2. Generate a JWT key using PowerShell:

   ```powershell
   [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
   ```

3. Start the application, a fresh PostgreSQL database and a local email inbox:

   ```powershell
   docker compose --env-file .env.course up --build -d
   ```

Default addresses:

- API: http://127.0.0.1:8081
- Swagger: http://127.0.0.1:8081/swagger-ui/index.html
- OpenAPI JSON: http://127.0.0.1:8081/v3/api-docs
- Database health: http://127.0.0.1:8081/actuator/health
- Local email inbox: http://127.0.0.1:8026

Compose uses the separate `task-management-course` project, port 5433 and
`course_postgres_data` volume. Do not point it at an existing ProjectFlow database.
Swagger is the browser interface for this assignment; there is no separate web app.

To run Java outside Docker, start only the dependencies and run the jar:

```powershell
docker compose --env-file .env.course up -d postgres mailpit
.\mvnw.cmd clean verify
java -Duser.timezone=UTC -jar target/task-management-app-0.0.1-SNAPSHOT.jar --server.port=8081
```

Use `./mvnw` on Linux/macOS. The application binds loopback by default; Compose
overrides the container bind address and publishes the HTTP port on host loopback.

## API

Register, then log in and pass `Authorization: Bearer <accessToken>` to protected
endpoints. In Swagger, paste the token into **Authorize**. JWTs expire after 15
minutes; log in again to obtain another. Passwords are BCrypt hashes. Registration
always creates USER; request-supplied roles are rejected. Roles are loaded from the
database on every authenticated request, so changes apply to existing JWTs.

| Area | Endpoints |
|---|---|
| Authentication | `POST /api/auth/register`, `POST /api/auth/login` |
| Profile and roles | `GET/PUT/PATCH /users/me`, `PUT /users/{id}/role` (ADMIN) |
| Projects | `POST/GET /api/projects`, `GET/PUT/DELETE /api/projects/{id}` |
| Tasks | `POST /api/tasks`, `GET /api/tasks?projectId=1`, `GET/PUT/DELETE /api/tasks/{id}` |
| Comments | `POST /api/comments`, `GET /api/comments?taskId=1`, `DELETE /api/comments/{id}` |
| Labels | `POST/GET /api/labels`, `PUT/DELETE /api/labels/{id}` |
| Attachments | `POST /api/attachments?taskId=1` (multipart `file`), `GET /api/attachments?taskId=1` |
| File download/delete | `GET /api/attachments/{id}/download`, `DELETE /api/attachments/{id}` |

Lists are paginated: `page=0&size=20`, maximum size 100. Attachment list responses
contain metadata; the download endpoint retrieves actual bytes from Dropbox.
OpenAPI describes request/response schemas. Invalid input returns 400; missing or
inaccessible project resources return 404; forbidden owner/admin operations return
403; data conflicts return 409; Dropbox failures return 502.

Project lists accept `name` (case-insensitive substring), `status`, `startFrom` and
`endTo` (inclusive project date bounds). Task lists accept `name`, `status`, `priority`,
`assigneeId`, `labelId`, `dueFrom` and `dueTo` (inclusive). Filters combine with AND
and always retain project access checks. Use ISO dates such as `2030-02-11`.

Example registration:

```json
{
  "username": "alice", "password": "replace-this-password",
  "email": "alice@example.com", "firstName": "Alice", "lastName": "Smith"
}
```

Login: `{"identifier":"alice","password":"replace-this-password"}`.
Create a project:

```json
{
  "name": "Course project", "description": "Team tasks",
  "startDate": "2030-01-01", "endDate": "2030-12-31",
  "status": "INITIATED", "memberIds": []
}
```

Create a task using the returned project ID:

```json
{
  "name": "Implement API", "description": "First task", "priority": "MEDIUM",
  "status": "NOT_STARTED", "dueDate": "2030-02-11",
  "projectId": 1, "assigneeId": 1, "labelIds": []
}
```

PUT replaces the editable fields. Project membership and task labels are sets of
existing IDs. To assign a task, use the project owner's ID or add the registered
user to `memberIds`. Profile PATCH retains omitted/null fields.

## Business rules

- Project creator is its owner. Owner and ADMIN update/delete projects and manage
  membership through project PUT. Members read the project and create/update tasks,
  comments and attachments. Outsiders cannot access project resources.
- Only project owner/ADMIN deletes tasks. A comment author, project owner or ADMIN
  can delete a comment. Project participants can delete task attachments.
- An assignee must be the owner or a member. Remove/reassign their tasks before
  removing a member. Task project ID is immutable.
- Project statuses are `INITIATED`, `IN_PROGRESS`, `COMPLETED`; task statuses are
  `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`; priorities are `LOW`, `MEDIUM`, `HIGH`.
  Status is editable metadata. Project status does not automatically follow tasks.
- Delete Dropbox attachments before deleting their task; task deletion removes
  comments and label links. Delete tasks before deleting a project. This avoids
  silently abandoning remotely stored files.
- Labels are a shared catalog editable by authenticated users. Deleting a label
  detaches it from tasks. Names are unique and colors use `#RRGGBB`.
- First administrator: register an account, then promote that account through an
  administrative database connection: `UPDATE users SET role='ADMIN' WHERE id=1;`
  (replace the ID with the intended account). Further promotions use the ADMIN API.
  No default administrator password is created.

## Dropbox and email

Use a dedicated Dropbox app with `files.content.write`, `files.content.read` and
`files.metadata.read` scopes. Set `DROPBOX_ACCESS_TOKEN`; short-lived tokens must be
replaced when expired. For automatic renewal, also configure a refresh token, app
key and app secret. Uploads accept 1 byte to 5 MB and persist the Dropbox file ID,
filename and UTC upload time. Files are retrieved/deleted by that ID, never a
client-supplied remote path. There is no local-disk or alternate-provider fallback.

Assignment changes email the new assignee. Comments email the assignee unless they
wrote the comment themselves. Every day at **09:00 UTC**, open assigned tasks due
today or tomorrow generate email reminders to the assignee. All timestamps use UTC.
This policy was explicitly selected for the course version.

Email runs asynchronously after database commit. Failed/rolled-back writes do not
send email. SMTP failures are logged without recipient/content/credentials and do
not undo task changes. This intentionally simple single-instance implementation
has no durable email queue, automatic retry, restart catch-up or multi-instance
reminder deduplication. Dropbox and the database do not share a distributed
transaction; failed metadata saves attempt upload cleanup, and deletion can be
retried when the provider already removed the file.

Compose routes mail into Mailpit without contacting real recipients. To use an
external SMTP service, configure `COMPOSE_SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`,
`SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` and `MAIL_FROM` in `.env.course`.

## Code map and checks

```text
controller/   Seven REST controllers
service/      CRUD, permissions, Dropbox and email
repository/   Six Spring Data repositories
model/        Six JPA entities and assignment enums
dto/          Validated requests and response records
mapper/       Explicit entity-to-response mapping
security/     JWT authentication and BCrypt login
config/       Clock, Dropbox, mail async and OpenAPI configuration
exception/    Shared HTTP error handling
```

One request follows `TaskController → TaskService → TaskRepository`. Services use
constructor injection and transactions; controllers return DTOs. Hibernate validates
the schema and Liquibase tracks the new migration in `public`.

```powershell
.\mvnw.cmd clean verify
```

Tests exercise real HTTP security, JPA and Liquibase with H2 in PostgreSQL mode.
CI repeats the same suite on PostgreSQL and checks packaged startup/restart and the
Docker application. Dropbox and SMTP are mocked in automated tests; a successful
build does not prove real account permissions or real email delivery. JaCoCo report:
`target/site/jacoco/index.html`.

Google Calendar and AWS deployment are outside this submission. A shared task board,
live Dropbox acceptance and measured load results
require separate evidence; they are not claimed by this README.

## Export for course submission

This branch preserves the original development history. For a new repository with
only the course files, export the tested commit using `git archive`, extract it to
an empty directory and initialize a new Git repository there. Set that repository's
default branch to `main` and review/merge subsequent work there. Do not copy `.env`,
`.env.course`, ignored local documentation, `target` or IDE files. A standard GitHub
fork keeps a relationship with the original repository; an independent repository
is clearer for this submission. No new remote repository is created automatically.

Source scope: [original assignment](https://github.com/mate-academy/jv-task-management-system/blob/d4cec234b87b25b281094914fe77e9ef97893684/README.md).
Reference supplied by the mentor: [TaskFlow](https://github.com/gubber230/taskflow-api).
