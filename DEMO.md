# Скрипт демо ProjectFlow для заказчика

Показывайте **серверный workspace** (`main`), не alpha. Alpha — отдельный браузерный прототип.

## Подготовка (один раз)

```powershell
Copy-Item .env.sample .env   # задайте POSTGRES_PASSWORD и JWT_SIGNING_KEY_BASE64
docker compose up -d --wait
.\mvnw.cmd -q -DskipTests package
java -jar target/task-management-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=workspace
```

Откройте <http://127.0.0.1:8080/app/>.

При первом запуске создайте ADMIN через bootstrap (см. README), затем двух USER:
`owner_demo` и `member_demo` (регистрация в UI) **или** один OWNER + добавление участника по ID.

## Сценарий ~10 минут

| Мин | Действие | Что сказать |
|-----|----------|-------------|
| 0–1 | Войти как владелец | Постоянная cookie-сессия, CSRF; данные в PostgreSQL |
| 1–2 | Создать проект, добавить участника | Чужой аккаунт не увидит проект |
| 2–4 | Задачи **A** и **B**, зависимость A→B, назначить member на обе, bucket WORK | Оценка 15+ мин; исполнитель только из команды |
| 4–5 | Войти как member → «Мои задачи» | B заблокирована с причиной; A доступна |
| 5–6 | Start/Complete A → B открывается; на B Fail → Retry → Start | История попыток не теряется |
| 6–7 | Карточка проекта: Gantt; what-if с большей оценкой | CPM Mon–Fri 09–17; what-if **не** пишет в БД |
| 7–8 | «Обзор»: риск портфеля + загрузка исполнителей | Показатели из сохранённых данных |
| 8–9 | Комментарий от owner на задачу member; метка; TXT/PNG вложение | Файлы на диске сервера (не Dropbox) |
| 9–10 | «Уведомления» у member | In-app через outbox; email/Telegram — позже |

## Что не обещать

См. таблицу **Deferred** в [README](README.md#deferred-explicit-technical-debt): Dropbox, email/Telegram, leveling, праздники, финKPI.

## Alpha (по желанию, отдельно)

<http://127.0.0.1:8080/alpha/> — локальные сценарии в браузере + серверный `POST /alpha/plan`.
Не смешивать ID/статусы alpha с PostgreSQL workspace.
