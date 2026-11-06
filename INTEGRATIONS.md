# External service APIs

Official sources checked on **2026-11-04**. This is an API map and configuration guide
for ProjectFlow, not a copy of vendor documentation. Recheck the relevant source before
changing an adapter. Dependency versions come from `pom.xml`; a documentation review
date does not imply an SDK upgrade.

| Integration | Project support | Official documentation |
|---|---|---|
| Dropbox API v2 | Attachment upload/download/delete, OAuth refresh, personal linking, backfill | [OAuth](https://docs.dropboxapi.com/dropbox-api/docs/oauth), [HTTP API](https://www.dropbox.com/developers/documentation/http/documentation), [Java SDK](https://github.com/dropbox/dropbox-sdk-java) |
| Telegram Bot API | Chat linking through `/start`, webhook, notification delivery | [Bot API](https://core.telegram.org/bots/api), [webhooks](https://core.telegram.org/bots/webhooks) |
| Email / SMTP | Plain text + HTML, IMMEDIATE/DAILY, user preferences | [Spring Boot mail](https://docs.spring.io/spring-boot/reference/io/email.html) |
| MEGA | Personal accounts, native SDK, encrypted sessions, attachment adapter | [SDK](https://github.com/meganz/sdk), [API header](https://github.com/meganz/sdk/blob/master/include/megaapi.h) |

## Supplying configuration

The tables below use **Java process environment variable names**.
The application also imports `.env` from its working directory as Java properties
(`spring.config.import` in [application.properties](src/main/resources/application.properties)).
Project-specific settings have explicit `${NAME:default}` bindings.
For standard Spring settings in that file, use canonical names such as `spring.mail.host`,
or supply `SPRING_MAIL_HOST` as a process environment variable.
Keep secrets local; only [.env.sample](.env.sample) belongs in the repository.

In [compose.yaml](compose.yaml), `app.environment` forwards database, JWT, server and
MEGA enablement/key settings. Adding another key to the Compose `.env` file does not
automatically pass it to the application container: Dropbox/Telegram/SMTP require
separate container environment configuration. Do not put secrets in Dockerfiles or build arguments.

## Dropbox

### API and permissions

| Adapter operation | External method | Source |
|---|---|---|
| Authorization and token refresh | `/oauth2/authorize`, `/oauth2/token` | [OAuth guide](https://docs.dropboxapi.com/dropbox-api/docs/oauth) |
| Linked account identification | `/2/users/get_current_account` | [Get Current Account](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/users/get-current-account) |
| Upload | `/2/files/upload` | [Upload](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload) |
| Download | `/2/files/download` | [Download](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/download) |
| Delete | `/2/files/delete_v2` | [Delete v2](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/delete-v-2) |

For offline access, include `token_access_type=offline` in the authorization request;
exchange the refresh token for a new access token using `grant_type=refresh_token`.
The operations above use `account_info.read`, `files.content.read` and
`files.content.write`; check the current reference and App Console configuration.
Each method page in the table lists its scopes.
Choose App Folder / Full Dropbox access when configuring the application; changing a
ProjectFlow path does not change that access model. [Source: OAuth](https://docs.dropboxapi.com/dropbox-api/docs/oauth).

Simple upload supports files up to 150 MiB; larger files require upload sessions.
ProjectFlow limits attachments to 5 MiB and restricts MIME types;
the provider limit does not expand the application contract. [Source: Upload](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload).

`pom.xml` pins `dropbox-core-sdk:7.0.0`. At the review date, the vendor README lists
8.0.2 and requires Java 21+ for the 8.x line. This work did not upgrade that dependency.
[Source: Java SDK](https://github.com/dropbox/dropbox-sdk-java).

### ProjectFlow configuration

| Variable | Purpose |
|---|---|
| `ATTACHMENT_STORAGE=dropbox` | Select Dropbox instead of local disk |
| `DROPBOX_APP_KEY`, `DROPBOX_APP_SECRET`, `DROPBOX_REFRESH_TOKEN` | Shared application account with access token refresh |
| `DROPBOX_ACCESS_TOKEN` | Alternative access token; expires without refresh |
| `DROPBOX_APP_FOLDER` | Root path, default `/projectflow` |
| `DROPBOX_MODE=app\|team`, `DROPBOX_TEAM_FOLDER` | Configured path selection; team mode requires a path |
| `DROPBOX_OAUTH_REDIRECT_URI` | Registered redirect URI for personal OAuth |
| `DROPBOX_CREDENTIAL_KEY_BASE64` | Separate 32 random bytes encoded as Base64; encrypts persisted personal refresh tokens |

Personal OAuth: `POST /api/dropbox/authorize` returns an `authorizeUrl` and signed
`state`. After browser authorization, submit the code and the same state through
authenticated `POST /api/dropbox/callback` with JSON `{"code":"…","state":"…"}`.
This exchange-completion API is not a public GET callback from Dropbox.
Configure the external redirect URI and return handler separately.
Status: `GET /api/dropbox`; unlink: `DELETE /api/dropbox/link`.

Personal linking also requires the credential key; without it, `oauthConfigured=false`
and authorization stops before provider calls. The authenticated encryption envelope is
bound to its owning user; corrupt/wrong-key credentials fail closed with
`409 DROPBOX_CREDENTIALS_UNAVAILABLE`, and a missing key when reading a linked credential
returns `503 DROPBOX_CREDENTIALS_NOT_CONFIGURED`. Neither case switches accounts or
returns plaintext/provider diagnostics. Preserve the key across restarts; loss/rotation
requires relinking. Shared environment-based credentials are configured separately.

Upgrade 023 deliberately clears old personal links and removes the plaintext token column.
The user selected relinking rather than automatic token migration. Existing attachment
metadata and remote bytes are untouched, and no Dropbox request occurs during migration.
Removing a stored credential does not revoke it at Dropbox. Schema rollback clears links
again; it does not recover erased tokens or make application-only downgrade safe.

When Dropbox is selected, a user's personal link handles new uploads; without it,
the shared store is used. Relinking and access to previously uploaded files require
separate acceptance checks; this document does not promise automatic migration.
Backfill `POST /api/attachments/backfill` is an existing ADMIN operation from local
storage to the shared Dropbox store, not to a personal account or MEGA.

Explicit attachment deletion treats Dropbox `path_lookup/not_found` as already deleted.
Other API/transport failures return `500 STORAGE_ERROR` without provider diagnostics;
the metadata and deletion activity transaction rolls back, so the same attachment can
be retried. An uncertain network failure may already have removed remote bytes; the
idempotent not-found handling lets a retry finish metadata cleanup. Database commit
failure after a successful remote delete is not a distributed atomic transaction.
Records without an external reference remain local even after a user links Dropbox.
Cloud references fail closed when Dropbox storage is disabled; they are not deleted
through the local fallback. Only optional cleanup of a secondary legacy local copy
remains best-effort after confirmed remote deletion.

### Team Space support boundary

The official Team Files Guide describes namespaces and `Dropbox-API-Path-Root`;
team-linked tokens also distinguish Select-User and Select-Admin. A folder path
alone is insufficient for Team Space access. [Source: Team Files](https://docs.dropboxapi.com/dropbox-api/docs/team-files).

The current [SdkDropboxFileClient](src/main/java/com/example/taskmanagement/attachment/application/SdkDropboxFileClient.java)
does not use `withPathRoot` or select a team member/administrator. `DROPBOX_MODE=team`
selects a folder but does not implement the full Dropbox Business / Team Space model.
That deployment requires additional implementation and real-account verification.

## Telegram

A bot and HTTPS webhook endpoint are required. Vendor methods used:
[`sendMessage`](https://core.telegram.org/bots/api#sendmessage),
[`setWebhook`](https://core.telegram.org/bots/api#setwebhook),
[`getWebhookInfo`](https://core.telegram.org/bots/api#getwebhookinfo).
`sendMessage.text` is limited to 4096 characters after entity parsing. Rate-limited
responses may include `retry_after`. Webhooks and `getUpdates` are mutually exclusive.
Configure webhook `secret_token`; Telegram sends it in the
`X-Telegram-Bot-Api-Secret-Token` header. [Source: Bot API](https://core.telegram.org/bots/api).

| Variable | Purpose |
|---|---|
| `TELEGRAM_BOT_TOKEN` | Bot token; empty disables the channel |
| `TELEGRAM_BOT_USERNAME` | Username for deep links |
| `TELEGRAM_WEBHOOK_SECRET` | Incoming webhook verification secret |
| `APP_PUBLIC_BASE` | Public application base URL for links |

The operator registers the webhook with `setWebhook`: URL
`https://<application-host>/api/telegram/webhook`, with `secret_token` matching
`TELEGRAM_WEBHOOK_SECRET`. Use a nonempty secret; current code checks the header
only when a secret is configured. The application does not call `setWebhook` itself.
Telegram permits `A-Z`, `a-z`, `0-9`, `_` and `-`, length 1–256, for this secret.
[Source: setWebhook](https://core.telegram.org/bots/api#setwebhook).

The user calls `POST /api/telegram/link`, opens the returned URL and sends `/start`
with the single-use token. Status: `GET /api/telegram`; mute:
`PUT /api/telegram/enabled`; unlink: `DELETE /api/telegram/link`.
Reading status also processes the delivery queue and may send messages.

Current adapter limits: [HttpTelegramBotClient](src/main/java/com/example/taskmanagement/integration/telegram/application/HttpTelegramBotClient.java)
sends JSON `sendMessage` requests but does not implement `retry_after` backoff or
split long messages. Code uses `disable_web_page_preview`, while the current
reference describes `link_preview_options`. These are compatibility review items
for a future sprint, not completed fixes. [Source: sendMessage](https://core.telegram.org/bots/api#sendmessage).

## Email / SMTP

There is no separate vendor HTTP API: ProjectFlow uses `JavaMailSender` and SMTP.
Spring Boot creates a sender when mail libraries and `spring.mail.host` are present.
Its documentation also shows connection/read/write timeouts because some defaults
allow unbounded waiting. [Source: Spring Boot mail](https://docs.spring.io/spring-boot/reference/io/email.html).

This document does not select an SMTP provider. Obtain the server address, port,
authentication, TLS and sender policies from the chosen provider.

| Variable | Purpose |
|---|---|
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP endpoint |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP credentials |
| `MAIL_FROM` | Sender address |
| `MAIL_BRAND_NAME`, `MAIL_THEME=default\|slate` | HTML appearance |
| `MAIL_DIGEST_CRON` | Digest schedule, default `0 0 8 * * *` |
| `APP_PUBLIC_BASE` | Application links |

If SMTP is not configured, **omit `SPRING_MAIL_HOST`**. Mail health is disabled in
this project; `health=UP` does not establish successful email delivery.
The following additional **Java properties** configure SMTP with STARTTLS;
use them only when the provider supports that mode:

```properties
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000
```

This sprint does not automatically enable these TLS/timeout settings.
SMTP property reference, including STARTTLS:
[Eclipse Angus Mail SMTP](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html).
Status: `GET /api/email`; mute: `PUT /api/email/enabled`;
mode: `PUT /api/email/digest` with `{"mode":"IMMEDIATE"}` or `{"mode":"DAILY"}`.
Reading status also processes the delivery queue and may send email.
Implementation: [EmailClientConfiguration](src/main/java/com/example/taskmanagement/integration/email/application/EmailClientConfiguration.java).

## MEGA — personal user account

The user selected personal accounts, the official SDK and unlinking with a warning.
S52 adds a native adapter using SDK **10.22.0**, commit
`76771e3b2603bbe0d1b61d0e70861e345ae96af5`.
There is no `ATTACHMENT_STORAGE=mega`: a linked personal account takes precedence
for new uploads; otherwise, the existing local/Dropbox store is used.

The official SDK is C++; API documentation is in
[`megaapi.h`](https://github.com/meganz/sdk/blob/master/include/megaapi.h), and the Java API is in
[`MegaApiJava.java`](https://github.com/meganz/sdk/blob/master/bindings/java/nz/mega/sdk/MegaApiJava.java).
The SDK README describes CMake/VCPKG and SWIG Java bindings. Here, Java 21 calls a
small project-owned C++ bridge through private stdin/stdout pipes without a shell.
Each operation starts a separate SDK process without a shared cache.
[Source: MEGA SDK](https://github.com/meganz/sdk).

The SDK requires an app key and supports session login/cache; this is not Dropbox OAuth.
[Source: Java API](https://raw.githubusercontent.com/meganz/sdk/master/bindings/java/nz/mega/sdk/MegaApiJava.java).
Neither MEGAcmd nor the unofficial MEGAJS library is used.
The SDK license is published in [LICENSE](https://github.com/meganz/sdk/blob/master/LICENSE);
review the chosen version and dependency terms before distribution.

### Configuration and connection

| Variable | Purpose |
|---|---|
| `MEGA_ENABLED=true` | Enable personal accounts (default false) |
| `MEGA_APP_KEY` | MEGA-registered application key |
| `MEGA_SESSION_KEY_BASE64` | Separate 32 random bytes encoded as Base64 for AES-256-GCM sessions |
| `MEGA_EXECUTABLE` | Bridge path; `/usr/local/bin/projectflow-mega` in the image |
| `MEGA_TIMEOUT_SECONDS` | Operation deadline: default 120, range 1–600 |

Persist the encryption key across restarts. Replacing it without re-encryption makes
old sessions unreadable and requires users to sign in again. Passwords and sessions
reach the native process through pipes, not command-line arguments. PostgreSQL stores
only the encrypted session. The session grants account access: attachments use
`ProjectFlow-u<userId>`, but this folder does not provide a restricted OAuth scope.

The Docker image includes a bridge built from the official SDK. Runtime remains Java 21;
Ubuntu 24.04 provides SDK/cURL compatibility. For a JAR outside the container, build the
bridge separately using [CMake](native/mega/CMakeLists.txt) and set `MEGA_EXECUTABLE`.
To verify the standalone image:

```powershell
docker build -f native/mega/Dockerfile -t projectflow-mega-bridge:local .
docker run --rm --network none projectflow-mega-bridge:local --self-test
```

The Workspace profile contains a Personal MEGA account form: email, password and,
when required, a six-digit 2FA code. Use HTTPS outside loopback. Password/MFA fields
are cleared after an attempt and are not stored in browser storage.

- `GET /api/mega`: status and account ID, without the session.
- `POST /api/mega/link`: JSON `{"email":"…","password":"…","mfaCode":null}`;
  supply a six-digit string instead of null for 2FA. Cookie sessions require CSRF;
  bearer authentication is also supported. OpenAPI: `/v3/api-docs/mega` under the alpha profile.
- `DELETE /api/mega/link`: removes the local link; files and metadata remain.
  The UI requires warning acknowledgement before unlinking or switching accounts.

A file reference is `mega:<accountId>:<nodeHandle>`. A project member's read uses the
uploader's link and verifies the original account ID. After unlinking or linking a
different account, old files are unavailable; reconnecting the original account
restores access. Existing local/Dropbox records are not migrated.
Deleting a MEGA attachment requires the original account to be available; metadata
is retained on failure. Unlinking does not revoke the session at MEGA: use MEGA's
security settings for revocation. Migration and orphan reconciliation after an
uncertain external failure are not implemented.

At most two SDK processes run concurrently. Excess requests return `MEGA_BUSY`
(503); deadlines return `MEGA_TIMEOUT` (504). SDK output and secrets are excluded
from errors; native libraries are not loaded into the JVM.
The bridge response must contain exactly one complete, bounded frame with valid UTF-8
and no NUL bytes. Success responses require four fields for login, two for upload and
one for download/delete; error responses require a single code. Truncated frames,
extra fields and trailing bytes return `MEGA_PROTOCOL_ERROR` without native diagnostics.

## Live connection acceptance

Automated tests use recording adapters for external services. Green CI does not
replace the following checks with dedicated test accounts:

1. Dropbox: upload → download and compare contents → delete; refresh after access
   token expiry; personal OAuth with valid/foreign state; separately verify the required Team Space model.
2. Telegram: link a chat, verify the webhook secret, receive a notification, mute/unlink;
   API failure and rate limiting. Real messages require explicit authorization.
3. SMTP: plain/HTML and DAILY delivery, provider TLS mode, invalid password,
   server unavailability and bounded timeouts. Real messages require authorization.
4. MEGA: login/2FA, upload/download/delete, second-member access, unlink/relink with
   the original and a different account. Build, offline self-test and the recording
   adapter are checked automatically; live acceptance requires a real account.

See [README](README.md) for verification of the current ProjectFlow server contract.
Do not automatically send test messages or change an external bot's webhook.
