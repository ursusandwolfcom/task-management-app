# Course requirements and review checklist

[README](../README.md) · [Architecture](ARCHITECTURE.md) · [API](API.md) · [Operations](OPERATIONS.md)

Scope is based on the [original assignment](https://github.com/mate-academy/jv-task-management-system/blob/d4cec234b87b25b281094914fe77e9ef97893684/README.md)
and the mentor's [TaskFlow reference](https://github.com/gubber230/taskflow-api).
MapStruct is an additional mandatory course technology explicitly confirmed by
the user. This checklist distinguishes implemented code from external acceptance.

## Implemented features

| Requirement | Implementation / review entry |
|---|---|
| User registration, authentication and roles | [AuthController](../src/main/java/com/example/taskmanagement/controller/AuthController.java), [UserController](../src/main/java/com/example/taskmanagement/controller/UserController.java) |
| Six assignment models and enum values | [model package](../src/main/java/com/example/taskmanagement/model) |
| Project CRUD, membership and authorization | [ProjectService](../src/main/java/com/example/taskmanagement/service/ProjectService.java) |
| Task CRUD, assignment and notifications | [TaskService](../src/main/java/com/example/taskmanagement/service/TaskService.java) |
| Comments and deletion | [CommentService](../src/main/java/com/example/taskmanagement/service/CommentService.java) |
| Shared labels and task labeling | [LabelService](../src/main/java/com/example/taskmanagement/service/LabelService.java) |
| Dropbox upload, retrieval and deletion | [AttachmentService](../src/main/java/com/example/taskmanagement/service/AttachmentService.java), [DropboxService](../src/main/java/com/example/taskmanagement/service/DropboxService.java) |
| Deadline/comment/update notifications | [EmailService](../src/main/java/com/example/taskmanagement/service/EmailService.java), [DeadlineReminderService](../src/main/java/com/example/taskmanagement/service/DeadlineReminderService.java) |
| Search and filters | Project/Task filter DTOs and service specifications |
| MapStruct usage | Six response mappings, nested/collection ID mapping, label creation and @MappingTarget update |
| Database migrations and health | Liquibase in public, ddl-auto=validate, /actuator/health |
| Swagger and setup documentation | /swagger-ui/index.html, /v3/api-docs and this documentation |
| Docker and CI | [Dockerfile](../Dockerfile), [Compose](../compose.yaml), [workflow](../.github/workflows/ci.yml) |
| Checkstyle and >=60% coverage | Maven verify and JaCoCo gate in [pom.xml](../pom.xml) |

## Contract and technology differences

- Attachment GET-by-task returns metadata; actual file bytes are retrieved through
  GET /api/attachments/{id}/download. The assignment describes retrieval at the
  list route; this split should be confirmed with the mentor.
- The course branch imports .env.course to protect the existing product's .env.
  A placeholder .env.sample is committed; both real secret files remain ignored.
- Java 21/PostgreSQL are retained instead of the example's Java 22/MySQL. The
  original assignment does not prescribe those exact versions/vendors.
- Roles use an enum on User rather than a separate Role entity. DTO conversion
  uses MapStruct; ordinary JPA getters/setters do not require Lombok.
- PUT replaces editable fields; project membership and task labels are ID sets.
  Status is manually editable. Deletion guards require attachment/task cleanup.
- Authentication uses Spring Security's JWT support rather than the example's JJWT.

## Remaining acceptance evidence

| Item | Status |
|---|---|
| Real Dropbox account upload/download/delete | Manual acceptance still required |
| Real inbox email delivery | Manual acceptance still required; Compose uses Mailpit |
| High-user-count scalability | Not established by load measurements |
| Shared task board | Must be supplied for the submitted repository |
| Per-task PRs and review history | Snapshot export does not transfer/satisfy this process requirement |
| Branch protections | Configure and verify in the new repository |
| Automatic deployment | Not implemented; CI verifies builds/tests/containers |
| AWS deployment | Optional in the assignment; outside this submission |
| Google Calendar | Advanced integration not implemented |

Passing CI establishes automated behavior on its exact commit. It does not
establish live-provider acceptance or complete satisfaction of repository process
requirements. Final assignment acceptance belongs to the mentor.
