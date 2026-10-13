# Task Management System

## Task lifecycle

The pure `task.domain` lifecycle supports start, complete, fail, cancel and retry.
Cancel is allowed from NOT_STARTED, IN_PROGRESS and FAILED; COMPLETED and CANCELLED
are terminal. `TaskExecution` retains immutable execution attempts with UTC timestamps:
retry preserves the failed attempt and the next start creates another. Start requires
actual predecessor completion supplied by the caller. Domain persistence, transactional
resource/access guards and execution HTTP endpoints are planned for later stages.
The browser alpha remains a separate prototype.

## Local development

The application uses Java 21, Spring Boot 4.1.1, and Maven Wrapper.
Dependencies: Spring Web MVC, Validation, Actuator, Spring Data JPA, Liquibase,
and the PostgreSQL JDBC driver.

1. Install JDK 21 and set `JAVA_HOME` to its installation directory.
2. Open this directory as a Maven project in your IDE; select JDK 21 as the project SDK.
3. Build and run the tests in PowerShell:

   ```powershell
   .\mvnw.cmd clean verify
   ```

4. Configure the local database. Install and start Docker with Compose support.
   If Docker is available only inside WSL, run the Docker commands in WSL from
   `/mnt/c/Data/Projects/java/task-management-app`.
   If `.env` does not exist yet, copy the sample and replace its password:

   ```powershell
   Copy-Item .env.sample .env
   ```

   Start PostgreSQL and wait until it is ready:

   ```powershell
   docker compose up -d --wait
   ```

5. Start the application from the project directory:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

6. Open `http://localhost:8080/actuator/health`; its `status` should be `UP`.
   Stop the application with `Ctrl+C`.

On Linux/macOS, use `./mvnw` instead of `.\mvnw.cmd`.
The first build requires internet access to download Maven and dependencies.
The application requires PostgreSQL when started normally. It reads `.env` as a
properties file; use simple `KEY=value` lines without shell `export` or quotes.
Environment variables take precedence. `.env` is ignored by Git; keep only
`.env.sample` in the repository.

PostgreSQL uses a persistent Docker volume and is exposed on localhost port 5432.
If the port is occupied, change `POSTGRES_PORT` in `.env`.
`docker compose down` stops the database and preserves its data.
Changing credentials in `.env` does not change users in an existing database volume.

### Database migrations and tests

Liquibase applies `src/main/resources/db/changelog/db.changelog-master.yaml` on
startup. The first migration creates the `task_management` schema for application
tables; Liquibase keeps its own tracking tables in `public`. Add each future
migration as a new file and include it in the master changelog. Do not edit migrations
that have already been applied to a shared database.

Hibernate uses `ddl-auto=validate`, so Liquibase manages table creation and changes.
The application and tests run in UTC, independently of the operating system timezone.
This also avoids JDBC startup failures with legacy timezone names such as `Europe/Kiev`.
The health endpoint also checks database connectivity without exposing credentials
or detailed diagnostics.

### Coverage gate

`clean verify` instruments tests with JaCoCo and requires at least 60% line coverage
across all application Java classes, including the alpha adapter. There are no class
exclusions. Open `target/site/jacoco/index.html` for the HTML report; XML and CSV reports
are generated in the same directory. Both H2 and PostgreSQL CI builds enforce the gate.

To verify that the gate rejects insufficient coverage, temporarily demand 100%:
`./mvnw -Dcoverage.minimum=1.00 verify`. A coverage violation must fail the build;
run the normal `clean verify` afterward. Do not use the override to lower the shared threshold.
The plugin configuration follows the [JaCoCo check documentation](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html).

### Architecture gate

ArchUnit runs with the normal tests and imports application classes only. It enforces
JDK-only dependencies for `planning.core` and `task.domain`, package acyclicity, and
allowed intermodule dependencies through public `api` packages. The graph core is the
explicit exception: callers use `planning.core`. Root application/config classes may
wire modules; business modules cannot depend back on bootstrap or other modules' internals.
The alpha adapter may call only the planning module and shared contracts.
The tests use the core [ArchUnit API](https://www.archunit.org/userguide/html/000_Index.html)
with the existing JUnit runner, without another test engine.

Local tests use an in-memory H2 database in PostgreSQL compatibility mode and do
not require Docker. The startup test verifies that Liquibase created the schema
and recorded the migration. This does not cover all PostgreSQL-specific behavior.
CI runs separate H2 and PostgreSQL 17 builds using `SPRING_DATASOURCE_*` environment
variables to override the test datasource. It also starts the packaged application
twice and checks health, exercising Liquibase's existing migration history on restart.

To run the tests against your local PostgreSQL from PowerShell, with the database running:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dspring.datasource.url=jdbc:postgresql://localhost:5432/task_management' '-Dspring.datasource.username=${POSTGRES_USER}' '-Dspring.datasource.password=${POSTGRES_PASSWORD}' test
```

If you changed the database name or port, adjust the URL in the command.
The username and password placeholders resolve from `.env` when Spring starts.

### Code style and CI

If IntelliJ IDEA Maven import fails with
`No implementation for ArtifactHandlerManager was bound`, select **Bundled (Maven 3)**
in **Settings → Build, Execution, Deployment → Build Tools → Maven**, disable
**Work offline**, and reload all Maven projects. This was verified with IntelliJ IDEA
Community 2025.2.6.2 and its bundled Maven 3.9.9. Command-line and CI builds use the
project Maven Wrapper.

`clean verify` checks the style of production and test Java sources, runs tests,
and packages the application. A style violation fails the build before compilation.
The shared rules are in `config/checkstyle/checkstyle.xml`: four spaces for indentation,
120 characters per line, no tabs or trailing whitespace, and no wildcard or unused imports.
`.editorconfig` supplies matching indentation settings for editors that support it.

To run only the style check in PowerShell:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress validate
```

GitHub Actions runs the same Maven Wrapper build on pushes and pull requests,
using Temurin Java 21 and a Maven dependency cache. It can also be started manually
from the Actions tab. The workflow is in `.github/workflows/ci.yml`.
After pushing these files to GitHub, inspect the CI run in the Actions tab.
To require a successful build before merging, add `Build, tests and Checkstyle`
as a required status check in the protection settings for the base branch.

The original assignment requirements follow below.

### Local alpha interface

The early application interface is available at `http://localhost:8080/alpha/`
when explicitly started with the `alpha` profile. It provides projects, task creation
and editing, priorities, minute estimates, due dates, dependencies, filters, and
start/complete/fail/retry actions for the prototype.

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=alpha'
```

Or after building:

```powershell
java -jar target/task-management-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=alpha
```

Start the PostgreSQL service as described above. The alpha server binds to loopback.
Projects and task states are stored only in this browser's local storage; clearing site
data removes them. The server calculates execution order and readiness using the real
JDK core and rejects cyclic changes before the browser saves them. It does not store
these projects/tasks in PostgreSQL or authenticate users. Domain CRUD, permissions,
execution history, calendars and resource guards remain planned features. This
prototype does not replace their acceptance criteria.

The page, its assets and `/alpha/plan` are disabled without the `alpha` profile. No
local docs or secrets are served. Frontend assets need no runtime CDN or bundler.
With Node 24 installed, run the frontend verification separately:

```powershell
npm.cmd ci --ignore-scripts
npm.cmd run check:alpha
npm.cmd run test:alpha
```

### Pure planning core

`com.example.taskmanagement.planning.core` is a JDK-only algorithm package, introduced
before domain CRUD. It provides immutable `TaskNode`, `DependencyEdge`, `GraphSnapshot`
and `ExecutionFacts`, priority-aware topological ordering, and dependency readiness.
Task adapters will map CRITICAL > HIGH > MEDIUM > LOW to explicit numeric weights.
Optional estimates are positive whole minutes, allowing fractional hours without
floating-point arithmetic. Estimates are not needed for ordering or readiness.

The sorter uses Kahn's algorithm with a priority queue: higher weight first, then
earlier due date (missing dates last), then smaller ID. Dependencies always win over
successor priority. Runtime is O(E + V log V), with O(V + E) graph storage. Snapshot
construction checks structural errors; the sorter rejects cycles with a typed
`CYCLIC_DEPENDENCY` diagnostic. Its unresolved IDs can include descendants of a cycle.

Execution order includes every task and does not simulate real completion. Available
tasks are only supplied candidates whose predecessors are actually completed; next
is a recommendation, without starting a task or reserving an assignee. Resource and
permission guards, calendars, CPM and domain REST endpoints follow in later sprints.

After building, try the pure core with `jshell --class-path target/classes`:

```java
import com.example.taskmanagement.planning.core.*;
import java.util.*;

var a = new TaskNode(1, 10, null, 30L);
var b = new TaskNode(2, 40, null, 60L);
var graph = GraphSnapshot.of(List.of(a, b), List.of(new DependencyEdge(1, 2)));
var sorter = new PriorityTopologicalSorter();
var readiness = new ReadinessCalculator(sorter);
sorter.executionOrder(graph).stream().map(TaskNode::id).toList(); // [1, 2]
readiness.availableTasks(graph, new ExecutionFacts(Set.of(), Set.of(1L, 2L)))
    .stream().map(TaskNode::id).toList(); // [1]
readiness.availableTasks(graph, new ExecutionFacts(Set.of(1L), Set.of(2L)))
    .stream().map(TaskNode::id).toList(); // [2]
```

Run only the core tests in PowerShell, without starting a Spring context or database:

```powershell
.\mvnw.cmd '-Dtest=GraphSnapshotTest,PriorityTopologicalSorterTest,ReadinessCalculatorTest' test
```

### Contribution process

This project is developed by one developer with an AI agent. The selected workflow
supersedes the original assignment's direct feature-to-main instructions below:
create `sprint/sNN` from `dev`, make small commits, review and test the sprint PR into
`dev`, then repeat tests on the merged `dev` commit before a release PR into `main`.
Use merge commits, check main CI/health, and synchronise `dev` before the next sprint.
Keep only `main`, `dev` and the current sprint branch locally and on origin; delete
integrated branches and their clean temporary worktrees before starting the next sprint.
Required checks are Maven Wrapper `clean verify`, current GitHub CI, and applicable
database/startup tests. Local planning documents and secrets are excluded from Git.

Manage tasks and projects effectively through a web-based application. This system enables task creation, assignment, progress tracking, and completion.


## If you work in a team

1. Create an organization on GitHub
   ![Create an organization](./description/create-an-organization.png)
2. Choose a `Free` plan
3. Use a name like `fe-feb20-team0` (your group + your team name)
4. It should belong to your personal account
   ![Set up your team](./description/set-up-your-team.png)
5. Add your teammates to the team by their usernames on Github
   ![Add your teammate](./description/add-your-teammate.png)

## Instructions

1. Create a new Spring Boot project
2. Create a new GitHub repo with `task-management-app` name (or any other you like)
    - if you work in a team the repo MUST belong to the organization;
    - configure `Branch protection rules` for the organization repository using the following tutorial:

      <details>
        <summary>Tutorial for branch protection rules</summary>

      Go to the repository settings
      STEP #1
      ![Add your teammate](./description/first-step-for-branch-protection-rules.png)

      STEP #2
      ![Add your teammate](./description/second-step-for-branch-protection-rules.png)

      Add the rule with only the next settings
      STEP #3
      ![Add your teammate](./description/third-step-for-branch-protection-rules.png)

      More information about all other settings at
      the [link](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/defining-the-mergeability-of-pull-requests/managing-a-branch-protection-rule)
      </details>

3. Connect a repo to a folder with your Spring Boot project (see the instructions on GitHub);
4. Setup checkstyle plugin in your project
5. Setup CI process to run the `mvn clean verify` command by adding a `.github/workflows/ci.yml` file
6. The `master` (or `main`) branch is a base for your feature PullRequests.
7. PRs should be merged thereafter review

## How to develop a feature

1. `Pull` the latest `master`.
2. Create a `your-feature-name` branch and `push` it to GitHub.
3. Open a Pull Request (`PR`) from your branch to the `master` (`main`).
4. Discuss branches and commit naming practices. Your branch, PR, and commit names should be consistent across the
   team. Example of PR names:
    - [DB]: prefix for liquibase scripts
    - [API] for scripts with API changes
5. Do not use forks, work in a single repo altogether!
6. Write your code, save it, and push it to GitHub.
7. Ask your teammate to review and approve if everything is OK.
8. If some fixes are required discuss the comments and repeat steps 5-7.
9. Mentors will review your codebase every day, but not PRs.

## Coding

- Add checkstyle plugin.
- Separate PR to the `main` branch for each task is required.
- 60%+ of the custom code should be covered with tests.
- Make sure to name your commits & branches meaningfully.
- Do not use forks, work in a single repo altogether (if you work in a team).

## App

### Requirements:

- Functional (what the system should do):
    - Web-based
    - User registration and authentication
    - Project management (CRUD operations)
    - Task management within projects
    - Assign and notify users about tasks
    - Add comments and attachments to tasks
    - Organize tasks with labels
    - Store attachments on Dropbox using its API
- Non-functional (what the system should deal with):
    - Scalable to handle a large number of users
    - Efficient retrieval of tasks, comments, and attachments
    - Secure storage of sensitive user data

### Models

1. **User**:
    - Username: String
    - Password: String
    - Email: String
    - First Name: String
    - Last Name: String

2. **Project**:
    - Name: String
    - Description: String
    - Start Date: LocalDate
    - End Date: LocalDate
    - Status: Enum: [INITIATED, IN_PROGRESS, COMPLETED]

3. **Task**:
    - Name: String
    - Description: String
    - Priority: Enum: [LOW, MEDIUM, HIGH]
    - Status: Enum: [NOT_STARTED, IN_PROGRESS, COMPLETED]
    - Due Date: LocalDate
    - Project ID: Long
    - Assignee ID: Long

4. **Comment**:
    - Task ID: Long
    - User ID: Long
    - Text: String
    - Timestamp: LocalDateTime

5. **Attachment**:
    - Task ID: Long
    - Dropbox File ID: String   // Store the reference to Dropbox
    - Filename: String
    - Upload Date: LocalDateTime

6. **Label**:
    - Name: String
    - Color: String

### Controllers

1. **Auth Controller**:
    - POST: /api/auth/register - User registration
    - POST: /api/auth/login - User authentication

2. Users Controller: Managing authentication and user registration
    - PUT: /users/{id}/role - update user role
    - GET: /users/me - get my profile info
    - PUT/PATCH: /users/me - update profile info

3. **Project Controller**:
    - POST: /api/projects - Create a new project
    - GET: /api/projects - Retrieve user's projects
    - GET: /api/projects/{id} - Retrieve project details
    - PUT: /api/projects/{id} - Update project
    - DELETE: /api/projects/{id} - Delete project

4. **Task Controller**:
    - POST: /api/tasks - Create a new task
    - GET: /api/tasks - Retrieve tasks for a project
    - GET: /api/tasks/{id} - Retrieve task details
    - PUT: /api/tasks/{id} - Update task
    - DELETE: /api/tasks/{id} - Delete task

5. **Comment Controller**:
    - POST: /api/comments - Add a comment to a task
    - GET: /api/comments?taskId={taskId} - Retrieve comments for a task

6. **Attachment Controller**:  // This is where we will interact with Dropbox API
    - POST: /api/attachments - Upload an attachment to a task (File gets uploaded to Dropbox and we store the Dropbox File ID in our database)
    - GET: /api/attachments?taskId={taskId} - Retrieve attachments for a task (Get the Dropbox File ID from the database and retrieve the actual file from Dropbox)

7. **Label Controller**:
    - POST: /api/labels - Create a new label
    - GET: /api/labels - Retrieve labels
    - PUT: /api/labels/{id} - Update label
    - DELETE: /api/labels/{id} - Delete label

### Dropbox Integration:

To store the attachments of tasks, we will use the Dropbox API. The general flow is:

1. When a user uploads an attachment, our backend will upload this file to Dropbox.
2. Dropbox returns a unique File ID after a successful upload.
3. We store this Dropbox File ID in our database against the attachment details.
4. When someone wants to retrieve/download the attachment, we use this File ID to fetch the file from Dropbox and serve it to the user.

This approach allows us to leverage Dropbox's robust file storage system, keeping our system lightweight and focused on task management.

Read more about [Dropbox API](./dropbox.md)

## Tasks

### Infrastructure

- Set up a task management board on a platform like Trello or Jira to keep track of progress.
- Initialize a new repository on GitHub or another platform for source code version control.
- Set up a CI/CD process to automate testing and deployment.
- Integrate a database migration tool, e.g., Liquibase or Flyway, to handle database schema changes.
- Add a health check endpoint to ensure the application is running and connected to the database.
- Containerize the application using Docker to ensure a consistent runtime environment.
- Store sensitive information such as API keys in a `.env` file, and add a `.env.sample` with placeholders to the repository.
- Implement API documentation using tools like Swagger to provide an interactive interface for testing and understanding the API.
- Update the `README.md` with setup instructions, API endpoints, and other relevant information once the project is complete.

### User Management

- Implement user registration and authentication mechanisms.
- Add user roles and permissions (e.g., ADMIN, USER) to control access to certain functionalities.
- Secure API endpoints using JWT tokens or other security mechanisms.

### Project Management

- Set up database tables and JPA entities for projects.
- Implement CRUD operations for projects.
- Ensure only authorized users can access, modify, or delete projects.

### Task Management

- Create tables and entities for tasks.
- Implement CRUD operations for tasks.
- Allow tasks to be assigned to users and notify assignees of new tasks.

### Comments

- Set up tables and entities for comments.
- Allow users to add, view, and delete comments related to tasks.

### Attachments

- Design the attachment entity to store metadata about files (filename, upload date, etc.), but the actual files will be stored on Dropbox.
- Integrate with the Dropbox API to upload, retrieve, and delete files.
    - When a new attachment is uploaded, save it to Dropbox and store the returned metadata in the application database.
    - For retrieval, fetch the metadata from the database and use the Dropbox API to fetch the actual file.
    - Deleting an attachment should remove the metadata from the database and also delete the file from Dropbox.
- Ensure only authorized users can upload, view, or delete attachments.

### Labels

- Implement database tables and entities for labels.
- Allow users to create, modify, and delete labels.
- Allow labels to be assigned to tasks for better organization.

### Notifications
- Integrate a notification system, like email or Telegram, to notify users about task deadlines, comments, and other updates.

### Optional:
- Deploy your app to the AWS

### Advanced
- Implement a search and filter mechanism for tasks and projects.
- Integrate with third-party applications, like Google Calendar, to provide additional functionalities.
