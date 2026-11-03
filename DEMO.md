# Скрипт демо ProjectFlow для заказчика

Показывайте **серверный workspace** (`main`), не alpha. Alpha — отдельный браузерный прототип.

## Подготовка (один раз)

```powershell
Copy-Item .env.sample .env   # POSTGRES_PASSWORD, JWT_SIGNING_KEY_BASE64
# Опционально Telegram: TELEGRAM_BOT_TOKEN, TELEGRAM_BOT_USERNAME, TELEGRAM_WEBHOOK_SECRET, APP_PUBLIC_BASE
# Опционально Email: SPRING_MAIL_* (см. .env.sample)
docker compose up -d --wait
.\mvnw.cmd -q -DskipTests package
java -jar target/task-management-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=workspace
```

Откройте <http://127.0.0.1:8080/app/>.

При первом запуске создайте ADMIN через bootstrap (см. README), затем двух USER:
`owner_demo` и `member_demo` (регистрация в UI) **или** один OWNER + добавление участника по ID.

## Сценарий ~12 минут

| Мин | Действие | Что сказать |
|-----|----------|-------------|
| 0–1 | Войти как владелец | Cookie-сессия, CSRF; данные в PostgreSQL |
| 1–2 | Создать проект, добавить участника | Чужой аккаунт не увидит проект |
| 2–3 | Праздники проекта (будни) | Нерабочие даты для CPM/Gantt |
| 3–5 | Задачи **A** и **B**, зависимость A→B, назначить member, bucket WORK | Оценка 15+ мин; исполнитель из команды |
| 5–6 | Войти как member → «Мои задачи» | B заблокирована; A доступна |
| 6–7 | Start/Complete A → B открывается; на B Fail → Retry → Start | История попыток |
| 7–8 | Карточка проекта: Gantt; what-if | Пн–Пт 09–17 + обед 13–14; what-if **не** пишет в БД |
| 8–9 | «Обзор»: риск + загрузка | Soft warn при пересечении планов одного исполнителя |
| 9–10 | Комментарий / метка / TXT-вложение | Файлы на диске (не Dropbox) |
| 10–11 | «Уведомления» у member | In-app; опционально Telegram link + Email mute |
| 11–12 | Activity на задаче | Append-only история событий |

## Что не обещать

См. **Deferred** в [README](README.md#deferred-explicit-technical-debt): Dropbox, rich HTML/digests, hard leveling, финKPI.

## Alpha (по желанию)

<http://127.0.0.1:8080/alpha/> — браузерный прототип + `POST /alpha/plan`.
Не смешивать ID/статусы alpha с PostgreSQL workspace.
