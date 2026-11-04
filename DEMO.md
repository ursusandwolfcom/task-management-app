# ProjectFlow customer demo script

Show the **server workspace** (`main`). Alpha is a separate browser prototype.
The interface currently uses Russian labels; names below are their English equivalents.

## Preparation (once)

```powershell
Copy-Item .env.sample .env   # Configure POSTGRES_PASSWORD and JWT_SIGNING_KEY_BASE64
# Optional Telegram: TELEGRAM_BOT_TOKEN, TELEGRAM_BOT_USERNAME, TELEGRAM_WEBHOOK_SECRET, APP_PUBLIC_BASE
# Optional email: SPRING_MAIL_* (see .env.sample)
docker compose up -d --wait
.\mvnw.cmd -q -DskipTests package
java -jar target/task-management-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=workspace
```

Open <http://127.0.0.1:8080/app/>.

On the first run, bootstrap an ADMIN (see README), then register two USER accounts,
`owner_demo` and `member_demo`, through the UI. Alternatively, use an existing
project owner and add a member by user ID. Project ownership is separate from the global user role.

## Approximately 12-minute walkthrough

| Minute | Action | Talking point |
|---|---|---|
| 0–1 | Sign in as the owner | Cookie session, CSRF and PostgreSQL persistence |
| 1–2 | Create a project and add a member | An unrelated account cannot see the project |
| 2–3 | Add project holidays on weekdays | Non-working dates affect CPM/Gantt |
| 3–5 | Create **A** and **B**, add dependency A → B, assign the member, set bucket WORK | Estimate at least 15 minutes; assignee must belong to the team |
| 5–6 | Sign in as the member and open My tasks | B is blocked; A is ready |
| 6–7 | Start/complete A; then fail → retry → start B | B becomes ready; attempt history is preserved |
| 7–8 | Open project Gantt and what-if | Mon–Fri 09:00–17:00, lunch 13:00–14:00; what-if does **not** write to the database |
| 8–9 | Open Overview: risk and workload | Soft warning when one assignee's plans overlap |
| 9–10 | Add a comment, label and TXT attachment | Local disk by default; Dropbox and personal MEGA require configuration |
| 10–11 | Open the member's Notifications | In-app notifications; optionally show Telegram linking and email mute |
| 11–12 | Open task activity | Append-only event history |

## Optional personal MEGA demonstration

Follow [MEGA setup and API](INTEGRATIONS.md#mega--personal-user-account).
Configure the native bridge, app key and separate session encryption key, then enable MEGA.
Use a dedicated test account in Profile, including its six-digit MFA code when required.
Upload and download a file, compare its contents, then delete it. Verify access as a
second project member and denial as an outsider. Demonstrate the warning before unlinking;
reconnecting the original account restores access to its remaining files.

Screenshots prepared during S53 use a mocked MEGA API and explicitly say so.
They do not establish successful live cloud transfers. A real-account acceptance run is still required.

## Claims to avoid

Use the current [integration limits](INTEGRATIONS.md) and
[deferred work](README.md#deferred-explicit-technical-debt).
Do not claim full Dropbox Team Space support, automatic storage migration,
remote MEGA session revocation on unlink, or production readiness based on mocked tests.
Earlier deferrals such as hard leveling and email digests have since been implemented;
the integration reference describes their current boundaries.

## Alpha (optional)

<http://127.0.0.1:8080/alpha/> is a browser prototype with `POST /alpha/plan`.
Keep alpha IDs and statuses separate from the PostgreSQL workspace.
