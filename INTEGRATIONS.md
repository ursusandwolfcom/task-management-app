# API внешних сервисов

Официальные источники проверены **2026-11-04**. Это карта API и инструкция для
ProjectFlow, а не копия документации поставщиков. Перед изменением адаптера повторно
проверьте соответствующий источник. Версии зависимостей определяет `pom.xml`;
дата проверки страницы не означает обновление SDK в проекте.

| Интеграция | В проекте | Официальная документация |
|---|---|---|
| Dropbox API v2 | Загрузка/скачивание/удаление вложений, refresh OAuth, личное подключение, backfill | [OAuth](https://docs.dropboxapi.com/dropbox-api/docs/oauth), [HTTP API](https://www.dropbox.com/developers/documentation/http/documentation), [Java SDK](https://github.com/dropbox/dropbox-sdk-java) |
| Telegram Bot API | Привязка чата через `/start`, webhook, доставка уведомлений | [Bot API](https://core.telegram.org/bots/api), [webhooks](https://core.telegram.org/bots/webhooks) |
| Email / SMTP | Plain text + HTML, IMMEDIATE/DAILY, настройки пользователя | [Spring Boot mail](https://docs.spring.io/spring-boot/reference/io/email.html) |
| MEGA | Личные аккаунты, нативный SDK, шифрование сессий, файловый адаптер | [SDK](https://github.com/meganz/sdk), [API header](https://github.com/meganz/sdk/blob/master/include/megaapi.h) |

## Как передавать настройки

Таблицы ниже используют **имена переменных окружения процесса Java**.
Приложение также импортирует `.env` из рабочего каталога как Java properties
(`spring.config.import` в [application.properties](src/main/resources/application.properties)).
Для собственных настроек проекта там предусмотрены явные `${NAME:default}`.
Для стандартных параметров Spring в таком файле используйте канонические имена,
например `spring.mail.host`, либо передайте `SPRING_MAIL_HOST` как переменную окружения.
Секреты хранятся локально, в репозиторий попадает только [.env.sample](.env.sample).

В [compose.yaml](compose.yaml) `app.environment` передаёт параметры БД, JWT, сервера
и включение/ключи MEGA. Наличие другого ключа в Compose `.env` не передаёт его автоматически
контейнеру приложения: для Dropbox/Telegram/SMTP нужна отдельная конфигурация окружения контейнера.
Не добавляйте секреты в Dockerfile или build arguments.

## Dropbox

### API и разрешения

| Операция адаптера | Внешний метод | Источник |
|---|---|---|
| Авторизация и обновление токенов | `/oauth2/authorize`, `/oauth2/token` | [OAuth guide](https://docs.dropboxapi.com/dropbox-api/docs/oauth) |
| Идентификация подключённого аккаунта | `/2/users/get_current_account` | [Get Current Account](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/users/get-current-account) |
| Загрузка | `/2/files/upload` | [Upload](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload) |
| Скачивание | `/2/files/download` | [Download](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/download) |
| Удаление | `/2/files/delete_v2` | [Delete v2](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/delete-v-2) |

Для offline-доступа OAuth-запрос включает `token_access_type=offline`; refresh token
обменивается на новый access token через `grant_type=refresh_token`.
Scopes для используемых операций: `account_info.read`, `files.content.read`,
`files.content.write`; проверяйте их в актуальном справочнике и App Console.
Scopes указаны в страницах соответствующих методов из таблицы выше.
Тип доступа App Folder / Full Dropbox выбирается при настройке приложения и не
меняется строкой пути в конфигурации ProjectFlow. [Источник: OAuth](https://docs.dropboxapi.com/dropbox-api/docs/oauth).

Обычный upload рассчитан на файлы до 150 MiB; для больших файлов нужен upload session.
ProjectFlow ограничивает вложение 5 MiB и дополнительно ограничивает MIME-типы;
лимит провайдера не расширяет контракт приложения. [Источник: Upload](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload).

В `pom.xml` установлен `dropbox-core-sdk:7.0.0`. На дату проверки README поставщика
показывает 8.0.2 и требует Java 21+ для ветки 8.x. Обновление зависимости здесь не
выполнялось. [Источник: Java SDK](https://github.com/dropbox/dropbox-sdk-java).

### Настройка ProjectFlow

| Переменная | Назначение |
|---|---|
| `ATTACHMENT_STORAGE=dropbox` | Выбор Dropbox вместо локального диска |
| `DROPBOX_APP_KEY`, `DROPBOX_APP_SECRET`, `DROPBOX_REFRESH_TOKEN` | Общий аккаунт приложения с обновлением access token |
| `DROPBOX_ACCESS_TOKEN` | Альтернативный access token; без refresh имеет ограниченный срок |
| `DROPBOX_APP_FOLDER` | Корневой путь, по умолчанию `/projectflow` |
| `DROPBOX_MODE=app\|team`, `DROPBOX_TEAM_FOLDER` | Выбор настроенного пути; при team путь обязателен |
| `DROPBOX_OAUTH_REDIRECT_URI` | Зарегистрированный redirect URI для личного OAuth |

Личный OAuth: `POST /api/dropbox/authorize` возвращает `authorizeUrl` и подписанный
`state`. После разрешения в браузере код и тот же state передаются авторизованным
`POST /api/dropbox/callback` с JSON `{"code":"…","state":"…"}`.
Это API для завершения обмена, а не публичный GET callback от Dropbox.
Внешний redirect URI и обработчик возврата должны быть согласованы отдельно.
Статус: `GET /api/dropbox`; отключение: `DELETE /api/dropbox/link`.

При выбранном Dropbox личное подключение используется для новых загрузок пользователя;
без него используется общий store. Переподключение аккаунта и доступ к ранее загруженным
файлам требуют отдельной приёмки; этот документ не обещает автоматическую миграцию.
Backfill `POST /api/attachments/backfill` — существующая ADMIN-операция local → общий
Dropbox store, а не перенос в личный аккаунт или MEGA.

### Граница поддержки Team Space

Официальный Team Files Guide описывает namespace и `Dropbox-API-Path-Root`; для
team-linked токенов также различаются Select-User и Select-Admin. Одного пути
недостаточно для доступа к Team Space. [Источник: Team Files](https://docs.dropboxapi.com/dropbox-api/docs/team-files).

В текущем [SdkDropboxFileClient](src/main/java/com/example/taskmanagement/attachment/application/SdkDropboxFileClient.java)
нет `withPathRoot` или выбора участника/администратора. `DROPBOX_MODE=team` выбирает
папку, но не реализует полную модель Dropbox Business / Team Space. Перед таким
внедрением нужны отдельные изменения и проверка реального аккаунта.

## Telegram

Нужны бот и HTTPS-адрес webhook. Используемые методы поставщика:
[`sendMessage`](https://core.telegram.org/bots/api#sendmessage),
[`setWebhook`](https://core.telegram.org/bots/api#setwebhook),
[`getWebhookInfo`](https://core.telegram.org/bots/api#getwebhookinfo).
`sendMessage.text` ограничен 4096 символами после разбора entities. При ограничении
частоты API может вернуть `retry_after`. Webhook и `getUpdates` взаимно исключаются.
Для webhook задаётся `secret_token`; Telegram передаёт его в заголовке
`X-Telegram-Bot-Api-Secret-Token`. [Источник: Bot API](https://core.telegram.org/bots/api).

| Переменная | Назначение |
|---|---|
| `TELEGRAM_BOT_TOKEN` | Token бота; пустое значение отключает канал |
| `TELEGRAM_BOT_USERNAME` | Username для deep link |
| `TELEGRAM_WEBHOOK_SECRET` | Секрет проверки входящего webhook |
| `APP_PUBLIC_BASE` | Публичный адрес приложения для ссылок |

Webhook регистрируется оператором через `setWebhook`: URL
`https://<адрес-приложения>/api/telegram/webhook`, `secret_token` равен настроенному
`TELEGRAM_WEBHOOK_SECRET`. Используйте непустой секрет; текущий код проверяет
заголовок только когда секрет задан. Само приложение `setWebhook` не вызывает.
В документации Telegram для секрета разрешены `A-Z`, `a-z`, `0-9`, `_`, `-`, длина
1–256. [Источник: setWebhook](https://core.telegram.org/bots/api#setwebhook).

Пользователь вызывает `POST /api/telegram/link`, открывает возвращённую ссылку
и отправляет `/start` с одноразовым token. Статус: `GET /api/telegram`;
mute: `PUT /api/telegram/enabled`; отвязка: `DELETE /api/telegram/link`.
Вызов статуса также запускает обработку очереди доставки и может отправить сообщения.

Граница текущего адаптера: [HttpTelegramBotClient](src/main/java/com/example/taskmanagement/integration/telegram/application/HttpTelegramBotClient.java)
отправляет JSON `sendMessage`, но не реализует backoff по `retry_after` или разбиение
длинного текста. Код использует `disable_web_page_preview`; актуальный справочник
описывает `link_preview_options`. Это пункты проверки совместимости будущего спринта,
а не уже выполненные исправления. [Источник: sendMessage](https://core.telegram.org/bots/api#sendmessage).

## Email / SMTP

Здесь нет отдельного HTTP API поставщика: ProjectFlow использует `JavaMailSender`
и SMTP. Spring Boot создаёт sender при наличии mail-библиотек и `spring.mail.host`.
В документации также приведены connection/read/write timeouts, поскольку некоторые
значения по умолчанию не ограничивают ожидание. [Источник: Spring Boot mail](https://docs.spring.io/spring-boot/reference/io/email.html).

Провайдер SMTP ещё не выбран этим документом. Адрес сервера, порт, способы
аутентификации, TLS и правила отправителя берутся у выбранного поставщика.

| Переменная | Назначение |
|---|---|
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP endpoint |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Учётные данные SMTP |
| `MAIL_FROM` | Адрес отправителя |
| `MAIL_BRAND_NAME`, `MAIL_THEME=default\|slate` | Оформление HTML |
| `MAIL_DIGEST_CRON` | Расписание дайджеста, по умолчанию `0 0 8 * * *` |
| `APP_PUBLIC_BASE` | Ссылки на приложение |

Если SMTP не настроен, **не задавайте `SPRING_MAIL_HOST`**. Проверка mail health
в проекте отключена; `health=UP` не подтверждает успешную доставку писем.
Ниже пример дополнительных **Java properties** для SMTP с STARTTLS; применяйте
его только если провайдер поддерживает этот режим:

```properties
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000
```

TLS/timeout-настройки в этом спринте не включены автоматически.
Справочник SMTP properties, включая STARTTLS:
[Eclipse Angus Mail SMTP](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html).
Статус: `GET /api/email`; mute: `PUT /api/email/enabled`;
режим: `PUT /api/email/digest` с `{"mode":"IMMEDIATE"}` или `{"mode":"DAILY"}`.
Чтение статуса также запускает доставку очереди и может отправить письма.
Реализация: [EmailClientConfiguration](src/main/java/com/example/taskmanagement/integration/email/application/EmailClientConfiguration.java).

## MEGA — личный аккаунт пользователя

Пользователь выбрал личные аккаунты, официальный SDK и unlink с предупреждением.
В S52 добавлен нативный адаптер поверх SDK **10.22.0**, commit
`76771e3b2603bbe0d1b61d0e70861e345ae96af5`.
`ATTACHMENT_STORAGE=mega` не используется: подключённый личный аккаунт имеет приоритет
для новых загрузок, а без подключения используется текущий local/Dropbox store.

Официальный SDK написан на C++; документация API находится в
[`megaapi.h`](https://github.com/meganz/sdk/blob/master/include/megaapi.h), Java API —
в [`MegaApiJava.java`](https://github.com/meganz/sdk/blob/master/bindings/java/nz/mega/sdk/MegaApiJava.java).
README SDK описывает CMake/VCPKG и Java-привязки на SWIG. Здесь Java 21 вызывает
собственный небольшой C++ bridge через закрытые stdin/stdout pipes без shell.
Каждая операция создаёт отдельный SDK-процесс без общего cache.
[Источник: MEGA SDK](https://github.com/meganz/sdk).

SDK требует app key и поддерживает session login/cache; это не Dropbox OAuth.
[Источник: Java API](https://raw.githubusercontent.com/meganz/sdk/master/bindings/java/nz/mega/sdk/MegaApiJava.java).
MEGAcmd и неофициальный MEGAJS не используются.
Лицензия SDK опубликована в [LICENSE](https://github.com/meganz/sdk/blob/master/LICENSE);
перед поставкой нужно проверить условия выбранной версии и её зависимостей.

### Настройка и подключение

| Переменная | Назначение |
|---|---|
| `MEGA_ENABLED=true` | Разрешить личные аккаунты (по умолчанию false) |
| `MEGA_APP_KEY` | Ключ приложения, зарегистрированного у MEGA |
| `MEGA_SESSION_KEY_BASE64` | Отдельные 32 случайных байта в Base64 для AES-256-GCM сессий |
| `MEGA_EXECUTABLE` | Путь к bridge; в образе `/usr/local/bin/projectflow-mega` |
| `MEGA_TIMEOUT_SECONDS` | Дедлайн операции: по умолчанию 120, диапазон 1–600 |

Ключ шифрования нужно сохранять при перезапуске. Замена без перешифрования делает
старые сессии нечитаемыми; потребуется повторный вход. Пароли и сессии передаются
нативному процессу через pipe, не как аргументы командной строки. В PostgreSQL
сохраняется только зашифрованная session. Сессия предоставляет доступ к аккаунту:
для вложений используется папка `ProjectFlow-u<userId>`, но это не ограниченный OAuth scope.

Docker-образ включает bridge из официального SDK. Runtime остаётся Java 21;
Ubuntu 24.04 выбрана для совместимости с cURL SDK. Для JAR вне контейнера bridge
собирается отдельно через [CMake](native/mega/CMakeLists.txt) и задаётся `MEGA_EXECUTABLE`.
Проверка сборки в отдельном образе:

```powershell
docker build -f native/mega/Dockerfile -t projectflow-mega-bridge:local .
docker run --rm --network none projectflow-mega-bridge:local --self-test
```

В профиле Workspace есть форма «Личный аккаунт MEGA»: email, пароль и, если требуется,
шестизначный код 2FA. Используйте HTTPS вне loopback. Пароль/MFA очищаются после
попытки и не сохраняются в browser storage.

- `GET /api/mega` — статус и account ID, без session.
- `POST /api/mega/link` — JSON `{"email":"…","password":"…","mfaCode":null}`;
  при 2FA вместо null передаётся шестизначная строка. Cookie-сессия требует CSRF;
  также поддерживается bearer auth. OpenAPI: `/v3/api-docs/mega` при профиле alpha.
- `DELETE /api/mega/link` — локально удаляет подключение; файлы и метаданные остаются.
  Перед отключением или сменой аккаунта UI требует подтвердить предупреждение.

Ссылка файла — `mega:<accountId>:<nodeHandle>`. Чтение участником проекта использует
подключение загрузившего пользователя и проверяет исходный account ID. После unlink
или подключения другого аккаунта старые файлы недоступны; повторный вход в исходный
аккаунт восстанавливает доступ. Старые local/Dropbox записи не мигрируют.
Удаление MEGA-вложения требует доступного исходного аккаунта; при отказе метаданные
не удаляются. Unlink не отзывает сессию у MEGA: для отзыва используйте настройки
безопасности MEGA. Перенос и очистка внешних orphan-объектов после неопределённого
сбоя не реализованы.

Одновременно работают не более двух SDK-процессов. Превышение возвращает `MEGA_BUSY`
(503), дедлайн — `MEGA_TIMEOUT` (504). Вывод SDK и секреты не включаются в ошибки;
нативные библиотеки не загружаются в JVM.

## Приёмка реального подключения

Автоматические тесты проекта используют recording-адаптеры для внешних сервисов.
Зелёный CI не заменяет следующие проверки с отдельными тестовыми аккаунтами:

1. Dropbox: upload → download с проверкой содержимого → delete; refresh после истечения
   access token; личный OAuth с корректным/чужим state; отдельно — нужная модель Team Space.
2. Telegram: привязка чата, секрет webhook, получение уведомления, mute/unlink;
   недоступность API и rate limiting. Реальная отправка требует явного разрешения.
3. SMTP: доставка plain/HTML и DAILY, режим TLS провайдера, неверный пароль,
   недоступность сервера, ограниченное время ожидания. Реальная отправка требует разрешения.
4. MEGA: login/2FA, upload/download/delete, доступ второго участника, unlink/relink
   исходного и другого аккаунта. Сборка, offline self-test и recording-адаптер
   проверяются автоматически; для живой приёмки нужен реальный аккаунт.

Проверка текущего серверного контракта ProjectFlow описана в [README](README.md).
Не отправляйте тестовые сообщения и не меняйте webhook внешнего бота автоматически.
