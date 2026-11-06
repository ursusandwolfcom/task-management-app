# Setup, verification and operations

[README](../README.md) · [API](API.md) · [Architecture](ARCHITECTURE.md) · [Course checklist](COURSE_CHECKLIST.md)

## Configuration

Copy `.env.sample` to `.env.course` once, then edit the placeholders locally.
The app imports `.env.course` as a Java properties file. Keep one `KEY=value` per
line, without shell `export` statements or surrounding quotes. Pass the same file
explicitly to Docker Compose with `--env-file .env.course`.

| Variable | Purpose / default |
|---|---|
| POSTGRES_DB | Fresh database; task_management_course |
| POSTGRES_USER | Database login; task_management |
| POSTGRES_PASSWORD | Required database password |
| POSTGRES_PORT | Host database port; 5433 |
| APP_PORT | Published Compose HTTP port; 8081 |
| JWT_SIGNING_KEY_BASE64 | Required Base64-encoded random key, at least 32 bytes |
| DROPBOX_ACCESS_TOKEN | Dropbox access token; required configuration |
| DROPBOX_REFRESH_TOKEN / DROPBOX_APP_KEY / DROPBOX_APP_SECRET | Optional refresh credentials |
| SMTP_HOST / SMTP_PORT | Host-run SMTP; localhost / 1025 |
| COMPOSE_SMTP_HOST | Compose SMTP host; mailpit |
| SMTP_USERNAME / SMTP_PASSWORD | Optional SMTP credentials |
| SMTP_AUTH / SMTP_STARTTLS | false by default; configure for your SMTP service |
| MAIL_FROM | Sender address; tasks@example.test |

Generate a key using Windows PowerShell or PowerShell 7:

```powershell
$keyBytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$generator.GetBytes($keyBytes)
[Convert]::ToBase64String($keyBytes)
$generator.Dispose()
```

Put the generated value into `.env.course`; do not commit it. Tests use their own
disposable key and substitute providers. Existing `.env` and product data are not
read by the course application.

## Docker Compose

```powershell
docker compose --env-file .env.course up --build -d
docker compose --env-file .env.course ps
docker compose --env-file .env.course logs --tail 100 app
```

The Docker build runs Maven verification. Runtime runs as a non-root user with UTC
defaults. Application and database ports are published on host loopback. The
separate course Compose project uses its own PostgreSQL volume and local Mailpit.

Stop the stack while retaining database data:

```powershell
docker compose --env-file .env.course down
```

Use a new database for this version; its migration is not an upgrade path from the
extended product. Hibernate validates the schema; Liquibase owns schema changes.

## Run Java on the host

Install Java 21, set JAVA_HOME and start only the dependencies:

```powershell
docker compose --env-file .env.course up -d postgres mailpit
.\mvnw.cmd clean verify
java -Duser.timezone=UTC -jar target/task-management-app-0.0.1-SNAPSHOT.jar --server.port=8081
```

Linux/macOS use `./mvnw`. APP_PORT controls the Compose mapping; the host-run jar
uses `--server.port`. The host-run app binds 127.0.0.1 by default.

## Verification

| Check | Command / evidence |
|---|---|
| Complete local build | `.\mvnw.cmd clean verify` |
| Focused regression | `.\mvnw.cmd -Dtest=CollaborationTest test` |
| Generated MapStruct code | `target/generated-sources/annotations/.../ResponseMapperImpl.java` |
| JUnit results | `target/surefire-reports` |
| JaCoCo report | `target/site/jacoco/index.html` |
| Database / container checks | GitHub Actions workflow in `.github/workflows/ci.yml` |

Default local tests use H2 in PostgreSQL compatibility mode. To run the same suite
against a disposable PostgreSQL database, set TEST_DATABASE_URL,
TEST_DATABASE_USERNAME and TEST_DATABASE_PASSWORD before running clean verify.
Use a dedicated test database; tests persist fixture data.

CI performs full H2 verification, repeats it on PostgreSQL, checks the packaged jar
twice and builds/starts/restarts the Docker application. Restart acceptance checks
that exactly one Liquibase migration remains recorded. Checkstyle and the 60% line
coverage gate are required; generated mapper output is compiled in the same build.

## Dropbox acceptance

Configure a dedicated Dropbox app with content read/write and metadata read
permissions. Short-lived access tokens need replacement or the optional refresh
token/app credentials. No fallback storage provider is configured.

1. Register/login, create a project/task and upload a small file through Swagger.
2. Confirm the stored Dropbox ID in the metadata response and the file in Dropbox.
3. Download via the attachment ID and compare the bytes to the source file.
4. Delete the attachment and verify both remote file and metadata are removed.
5. Verify an unrelated account cannot list/download/delete that task's attachment.

Automated tests use a substituted SDK client. Passing tests does not prove real
account scopes, token renewal or provider availability. Dropbox and PostgreSQL
cannot commit atomically; investigate failures across their boundary before
manually changing attachment metadata.

## Email acceptance

Compose defaults to Mailpit at http://127.0.0.1:8026 and does not deliver to real
recipients. Assign a task to another project participant and add a comment as its
owner; inspect the captured messages. An assignee's own comment does not notify
that same assignee. Reassigning to the same person does not send a new assignment.

Open assigned tasks due today/tomorrow receive reminders at 09:00 UTC. Completed
and unassigned tasks are excluded. There is no restart catch-up or durable retry.
To test delivery to a real inbox, configure the external SMTP variables and use a
recipient account you control. SMTP failure does not undo saved application data.

## First administrator

Register an account, note its ID and use an administrative database connection to
promote the intended account:

```sql
UPDATE users SET role = 'ADMIN' WHERE id = 1;
```

Replace 1 with the verified account ID. Further changes use `PUT /users/{id}/role`.
No default administrator account or password is created.

## Troubleshooting

| Symptom | Check |
|---|---|
| Startup rejects JWT configuration | Generate a valid Base64 key with at least 32 random bytes |
| Database connection/schema failure | Start the course database; check its port/password and Liquibase logs |
| Mapper bean/generated class missing | Reimport Maven, enable annotation processing, run clean compile |
| 401 from protected API | Log in again and pass the access token in the Authorization header |
| 404 for an existing project/task | Confirm the caller is owner/member or ADMIN |
| 409 deleting a project/task | Delete tasks/Dropbox attachments in the documented order |
| 502 for a file operation | Check Dropbox token/scopes/account and provider availability |
| No external email | Compose sends to Mailpit; check SMTP settings for real delivery |

## Export to a new repository

Export the tested commit using Git so ignored secrets, local docs and build output
are excluded:

```powershell
git archive --format=zip --output=course-submission.zip HEAD
```

Extract the archive into a new empty directory. Create an empty GitHub repository
without starter files, then run these commands in the extracted directory:

```powershell
git init -b main
git add .
git commit -m "Initial course submission"
git remote add origin https://github.com/YOUR_LOGIN/task-management-course.git
git push -u origin main
```

Replace YOUR_LOGIN and the repository name. Confirm the new repository's Actions
run succeeds. A snapshot export starts new Git history and does not transfer PRs,
approvals, branch protections, task boards or Actions history. Discuss the course's
per-task PR requirement with the mentor and use genuine reviewed PRs for subsequent
work. Creating a new repository and deployment are separate user actions.
