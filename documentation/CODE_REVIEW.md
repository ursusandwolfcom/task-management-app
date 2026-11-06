# Course code review and persistence audit

[README](../README.md) · [Architecture](ARCHITECTURE.md) · [Spring lifecycle](SPRING_LIFECYCLE.md)

Review date: 2026-11-06. Scope: course application, MapStruct, JPA relationships,
bean lifecycle and tests. Method: source/config inspection, Checkstyle, controlled
transaction regressions and Hibernate SQL counters. External-provider and load
acceptance remain separate.

## Findings addressed

| Finding | Evidence / impact | Resolution |
|---|---|---|
| Major: attachment access read before locking | Membership could already be loaded when a request waited for the lock. The pre-fix test returned 409 instead of 404; successful unauthorized remote deletion was not demonstrated. | Lock before authorization. Regression verifies 404, retained metadata and no Dropbox call. |
| Minor: explicit list sort masked by repository ordering | Fixed ID-desc ordering preceded client sorting for comments/attachments. | Pageable controls sort; descending ID remains the default. Ascending-comment regression added. |
| Minor: filename control characters | Old less-than-32 predicate missed DEL/C1 controls. | Character.isISOControl plus HTTP boundary tests. |
| Minor: duplicated limits and search escaping | File/password limits and literal LIKE escaping could drift. | Focused AttachmentLimits, PasswordPolicy and SearchPatterns shared by their actual consumers. |
| Minor: independent JWT duration values | Expiration used 15 minutes, response separately used 900 seconds. | One Duration supplies both; issuer/key-size limits are named. |
| Minor: generated scaffolding | Empty POM metadata, unordered imports and unnecessary qualified signature types. | Removed placeholders and normalized imports; preserved intentional JPA accessors. |
| Minor: hidden JWT infrastructure creation | Service constructed its own encoder. | Managed key/encoder/decoder beans and constructor injection; container graph verified. |
| Test isolation | New reminder fixtures caused four emails where another test expected two. | Performance test removes its due-task fixtures in finally; combined tests pass. |

No unused product modules, placeholder TODO implementations, printStackTrace calls
or broad swallowed exceptions were found in the reviewed production sources.
JPA accessors, generated MapStruct output and credential-redacting toString methods
serve defined purposes. Small controller similarities do not justify generic CRUD
inheritance or a shared service-locator layer.

## Relationship ownership

| Relationship | Owning side / storage | Lifecycle |
|---|---|---|
| Project → owner | Project.owner / owner_id | Mandatory LAZY to-one; no cascade into User |
| Project → members | Project.members / project_members | LAZY many-to-many; Project owns join rows |
| Task → project | Task.project / project_id | Mandatory LAZY to-one; no cascade into Project |
| Task → assignee | Task.assignee / assignee_id | Optional LAZY to-one; null maps safely to DTO |
| Task → labels | Task.labels / task_labels | LAZY many-to-many; shared labels survive deletion |
| Comment → task/user | Comment.task/user / foreign keys | Mandatory LAZY to-one; author lifecycle is independent |
| Attachment → task | Attachment.task / task_id | Mandatory LAZY to-one; service coordinates remote cleanup |

Required join nullability matches the migration. Join tables enforce composite
primary keys and foreign keys. No CascadeType.ALL/REMOVE targets shared users or
labels. Unused inverse collections are omitted; entities enter use cases as IDs
and managed references, not detached request graphs.

## Fetching and LazyInitializationException

All associations remain LAZY and open-in-view=false. MapStruct completes conversion
inside service transactions. The JPA test demonstrates that an unfetched entity
collection fails after EntityManager close while a service DTO remains serializable
after its transaction closes.

Paged members/labels use batch loading in the same transaction. Collection fetch
joins are not combined with pagination. The fail_on_pagination_over_collection_fetch
guard prevents a future query from silently paginating a collection join in memory.

| Small-fixture measurement | Before | After |
|---|---|---|
| Project page: 12 rows with members | 3 statements | 3 statements |
| Task page: 12 rows with labels | 5 statements | 4 statements |
| Reminder selection using assignee email | 2 statements | 1 statement |

Owner/admin checks avoid unnecessary membership loading. The reminder query uses
only a to-one join fetch for its required assignee data; row pagination stays in
the database and entity default fetching stays LAZY. Tests enforce bounded page
queries and complete DTO IDs. These measurements do not establish throughput,
latency or high-user-count scalability.

References: [Hibernate fetching](https://docs.hibernate.org/orm/7.1/introduction/html_single/#association-fetching)
and [LazyInitializationException](https://docs.hibernate.org/orm/7.2/javadocs/org/hibernate/LazyInitializationException.html).

## Spring lifecycle

Stateless services and generated mappers use singleton scope. No prototype is needed.
Constructor/factory parameters define initialization dependencies; Boot initializes
Liquibase before JPA validation. Transaction and async proxies remain active.
See the [lifecycle walkthrough](SPRING_LIFECYCLE.md).

## Validation and limits

Focused MapStruct verification passed 12 API tests; general review verification
passed 16 tests; combined JPA/lifecycle/security/notification verification passed 14
tests. These successful runs had no failures, errors or skips. Failed pre-fix
experiments establish the reason for fixes and are not counted as successful checks.

The submission also needs full clean verify and H2/PostgreSQL/container CI on the
exact published commit; consult the matching Actions run. No remaining blocker was
identified in the reviewed source. Live integrations, cross-system atomicity,
durable email retries, large-load evidence and repository process requirements
remain the documented acceptance boundaries.
