# Start here: original Mate assignment review

This guide is for reviewing the whole project against the
[original assignment](https://github.com/mate-academy/jv-task-management-system/blob/d4cec234b87b25b281094914fe77e9ef97893684/README.md).
The repository has grown beyond that assignment. Its frontend, graph/planning and MEGA
features are later extensions; their presence does not prove assignment compliance.
[The S56 packet](S56_HUMAN_CODE_REVIEW.md) reviews one PR, not the whole assignment.

The repository's default branch is main. PR #99 targets dev and is not yet integrated;
changes described there must not be assumed present in main. Use the guide and code
from the same selected branch/commit when reviewing.

## The six original models

The implementation suffixes persistence classes with Entity. These are the JPA models;
records ending in Data/View and interfaces ending in Operations are inputs, outputs and
application contracts. The assignment does not prescribe this package layout.

| Assignment model | JPA class | Important field mapping / difference |
|---|---|---|
| User | [UserEntity](../src/main/java/com/example/taskmanagement/identity/persistence/UserEntity.java) | username, email, firstName, lastName; passwordHash stores a password hash rather than a recoverable password |
| Project | [ProjectEntity](../src/main/java/com/example/taskmanagement/project/persistence/ProjectEntity.java) | name, description, startDate, endDate, status; owner/timezone/version/revisions are additions |
| Task | [TaskEntity](../src/main/java/com/example/taskmanagement/task/persistence/TaskEntity.java) | name, description, priority, status, dueDate, projectId, assigneeId; execution/planning fields are additions |
| Comment | [CommentEntity](../src/main/java/com/example/taskmanagement/collaboration/persistence/CommentEntity.java) | taskId, authorId (= assignment User ID), text, createdAt (= timestamp); time is UTC Instant instead of LocalDateTime |
| Attachment | [AttachmentEntity](../src/main/java/com/example/taskmanagement/attachment/persistence/AttachmentEntity.java) | taskId, fileName, createdAt; externalId stores the Dropbox file ID in Dropbox mode, or another provider's reference; storageKey is the local key |
| Label | [LabelEntity](../src/main/java/com/example/taskmanagement/collaboration/persistence/LabelEntity.java) | name, color; projectId, normalized name and version are additions |

Logical database relationships (entities generally store IDs, not @ManyToOne objects):

```text
UserEntity 1 <----- * TaskEntity       (assigneeId)
ProjectEntity 1 --- * TaskEntity       (projectId)
TaskEntity 1 ------ * CommentEntity    (taskId)
UserEntity 1 ------ * CommentEntity    (authorId)
TaskEntity 1 ------ * AttachmentEntity (taskId)
TaskEntity * ----- * LabelEntity       (task_labels table)
```

## Follow one request, not every class

The [controller and endpoint table](CONTROLLERS.md) links all seven controller areas
from the assignment directly to their source files, including separate DELETE handlers.

Each functional package uses: web = HTTP controllers, api = DTOs/contracts,
application = use cases/transactions, persistence = JPA/JDBC and repositories.

| Original feature | Start reading | Continue to |
|---|---|---|
| Registration, login, profiles, roles | security/web/AuthController.java and identity/web/UserController.java | identity/application/UserService.java; JWT/cookie configuration under config and security |
| Project CRUD | project/web/ProjectController.java | project/application/ProjectService.java, persistence/ProjectRepository.java |
| Task CRUD and assignment | task/web/TaskController.java | task/application/TaskService.java, persistence/TaskRepository.java |
| Comments | collaboration/web/CommentController.java | collaboration/application/CommentService.java |
| Labels and assignment to tasks | collaboration/web/LabelController.java and TaskLabelController.java | collaboration/application/LabelService.java |
| Files and Dropbox | attachment/web/AttachmentController.java | attachment/application/AttachmentService.java, AttachmentStorageConfiguration.java and SDK adapter |
| Notifications | notification/application/NotificationService.java | integration/outbox, integration/email and integration/telegram |

Paths in this table are relative to src/main/java/com/example/taskmanagement/.
Task/project DELETE handlers are in workflow/web; project deletion coordinates related
modules. This split is a navigation cost and should be considered during maintainability
review. Start with the six entities and one controller-service-repository path above.

For a first assignment pass, defer frontend/, workspace, alpha, planning/core, portfolio,
timesheets, capacities, calendars and MEGA-specific classes. They are real product scope,
but are not baseline requirements and must not substitute for checking the baseline.

## Actual differences and incomplete requirements

- Task status adds FAILED/CANCELLED to the three source values. Project declares the
  source enum values, but currently remains INITIATED through the exposed metadata API;
  status transitions are not implemented. The source does not specify their policy.
- Comment/attachment timestamps use UTC Instant rather than the source's LocalDateTime.
  Labels are project-scoped and their list requires projectId. Updates/deletes can require
  an expected version; task/project deletion has later history/dependency/emptiness guards.
  These are contract differences, not just class renames.
- Local storage is the default and MEGA is additional. Baseline Dropbox behavior must
  be reviewed with ATTACHMENT_STORAGE=dropbox; a successful local demo does not verify it.
- Source /users routes are added as aliases in S56 alongside /api/users. Until PR #99 is
  integrated, do not assume those aliases or encrypted Dropbox credentials are in main.
- Deadline reminders and general unattended push delivery remain planned (#101/#102).
  Existing assignment/comment/lifecycle notifications do not fulfill the deadline case.
- CI exists; target deployment automation is still planned (#103). Measured scalability,
  live Dropbox acceptance and accessible task-board evidence remain #104–#106.
- Green tests and the 60% line-coverage gate do not establish complete source compliance.
  The S56 review found a missing outer-factory routing path despite green CI; its hotfix
  and new regressions are documented in the S56 packet.

Reviewer questions: which contract differences are acceptable for assignment evaluation,
which must be adapted, and whether the later extension scope obscures the six core use
cases enough to justify a separate simplification task. This guide improves navigation;
it does not declare every difference accepted or every original requirement complete.
