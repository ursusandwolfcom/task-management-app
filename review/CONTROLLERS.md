# Assignment controllers: packages, classes and endpoints

Compare with the [original controller list](https://github.com/mate-academy/jv-task-management-system#controllers).
Base Java package: `com.example.taskmanagement`.
Every class name below opens the implementation in the same Git revision as this document.

| Assignment controller | Package below the base package | Implementation | Endpoints |
|---|---|---|---|
| Auth | `security.web` | [AuthController](../src/main/java/com/example/taskmanagement/security/web/AuthController.java) | `POST /api/auth/register`<br>`POST /api/auth/login` |
| Users | `identity.web` | [UserController](../src/main/java/com/example/taskmanagement/identity/web/UserController.java) | `GET /users/me`<br>`PUT /users/me`<br>`PATCH /users/me`<br>`PUT /users/{id}/role` |
| Project | `project.web`<br>`workflow.web` | [ProjectController](../src/main/java/com/example/taskmanagement/project/web/ProjectController.java)<br>[ProjectDeletionController](../src/main/java/com/example/taskmanagement/workflow/web/ProjectDeletionController.java) handles DELETE | `POST /api/projects`<br>`GET /api/projects`<br>`GET /api/projects/{id}`<br>`PUT /api/projects/{id}`<br>`PATCH /api/projects/{id}`<br>`DELETE /api/projects/{id}` |
| Task | `task.web`<br>`workflow.web` | [TaskController](../src/main/java/com/example/taskmanagement/task/web/TaskController.java)<br>[TaskDeletionController](../src/main/java/com/example/taskmanagement/workflow/web/TaskDeletionController.java) handles DELETE | `POST /api/tasks`<br>`GET /api/tasks`<br>`GET /api/tasks/{id}`<br>`PUT /api/tasks/{id}`<br>`PATCH /api/tasks/{id}`<br>`DELETE /api/tasks/{id}` |
| Comment | `collaboration.web` | [CommentController](../src/main/java/com/example/taskmanagement/collaboration/web/CommentController.java) | `POST /api/comments`<br>`GET /api/comments?taskId=...`<br>`DELETE /api/comments/{id}` |
| Attachment | `attachment.web` | [AttachmentController](../src/main/java/com/example/taskmanagement/attachment/web/AttachmentController.java) | `POST /api/attachments`<br>`GET /api/attachments?taskId=...`<br>`GET /api/attachments/{id}/content` downloads bytes<br>`DELETE /api/attachments/{id}` |
| Label | `collaboration.web` | [LabelController](../src/main/java/com/example/taskmanagement/collaboration/web/LabelController.java) | `POST /api/labels`<br>`GET /api/labels?projectId=...`<br>`PUT /api/labels/{id}`<br>`DELETE /api/labels/{id}` |

Task-label associations are handled by
[TaskLabelController](../src/main/java/com/example/taskmanagement/collaboration/web/TaskLabelController.java)
in `collaboration.web`: `GET /api/tasks/{taskId}/labels`, and
`PUT/DELETE /api/tasks/{taskId}/labels/{labelId}`.

## Select the correct revision

- The reviewer originally opened the repository root, which defaults to `main`.
  This document is published in the active S56 PR #99 targeting `dev`; integration is pending.
- Original `/users/...` routes are added in this PR. The released main code still uses
  `/api/users/...`; the PR keeps those paths as aliases alongside the original routes.
- The frontend is later scope beyond the Java assignment. The links above lead directly
  to backend classes; frontend code is not a prerequisite for reading these implementations.

## Contract details to check

Paths alone are not a claim of exact assignment compliance. Some mutations require an
expected `version`, labels are project-scoped, and deletion has history/dependency guards.
Attachment listing returns metadata; its separate `/content` route serves file bytes.
See the [six-model mapping and known differences](MATE_ASSIGNMENT_REVIEW.md) before
evaluating these choices. Swagger's `assignment` group presents the same core routes
when the existing local `alpha` documentation profile is enabled; default startup keeps
documentation and the alpha prototype disabled.
