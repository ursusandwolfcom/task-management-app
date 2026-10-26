package com.example.taskmanagement.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.ConcurrentCalls;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import com.example.taskmanagement.task.domain.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class TaskIntegrationTest {
    private static final String PASSWORD = "task-test-passphrase";
    @Autowired
    private TaskOperations tasks;
    @Autowired
    private ProjectOperations projects;
    @Autowired
    private ProjectCoordination coordination;
    @Autowired
    private IdentityOperations identity;
    @Autowired
    private TokenService tokens;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private UserView owner;
    private UserView member;
    private UserView outsider;
    private ProjectView project;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        owner = register("task_owner");
        member = register("task_member");
        outsider = register("task_outsider");
        project = projects.create(owner.id(), new ProjectData("Tasks", "Description",
                LocalDate.of(2026, 10, 14), LocalDate.of(2027, 10, 14), "UTC"));
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
    }

    @Test
    void pageBoundsRetainModuleErrorsAcrossTaskAndProjectLists() throws Exception {
        var task = create(member.id());
        String auth = bearer(member);
        for (String path : List.of("/api/tasks/" + task.id() + "/attempts",
                "/api/projects/" + project.id() + "/tasks", "/api/projects",
                "/api/projects/" + project.id() + "/members")) {
            for (int[] bounds : new int[][] {{-1, 20}, {1000001, 20}, {0, 0}, {0, 101}}) {
                mvc.perform(get(path).header("Authorization", auth)
                        .param("page", Integer.toString(bounds[0])).param("size", Integer.toString(bounds[1])))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            }
            mvc.perform(get(path).header("Authorization", auth).param("page", "1000000").param("size", "100"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Test
    void malformedDueDatesReturnValidationErrorsWithoutChangingTaskOrProject() throws Exception {
        var task = create(member.id());
        String auth = bearer(member);
        var revision = projects.read(owner.id(), project.id()).inputRevision();
        for (String date : List.of("not-a-date", "2026-02-30", "2026-13-01", "+9999999999-01-01")) {
            for (String method : List.of("POST", "PUT", "PATCH")) {
                String path = method.equals("POST") ? "/api/projects/" + project.id() + "/tasks"
                        : "/api/tasks/" + task.id();
                var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                        org.springframework.http.HttpMethod.valueOf(method), path);
                String fields = "\"name\":\"Invalid\",\"priority\":\"HIGH\",\"dueDate\":\"" + date + "\"";
                if (!method.equals("POST")) {
                    fields += ",\"version\":" + task.version();
                }
                mvc.perform(request.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{" + fields + "}"))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            }
        }
        assertThat(tasks.read(member.id(), task.id())).isEqualTo(task);
        assertThat(tasks.list(member.id(), project.id(), 0, 20).totalElements()).isEqualTo(1);
        assertThat(projects.read(owner.id(), project.id()).inputRevision()).isEqualTo(revision);
    }

    @Test
    void patchRejectsSnapshotWhoseVersionDiffersFromRequestEvenWhenRequestMatchesLatestVersion() {
        var stale = create(member.id());
        var latest = tasks.replace(member.id(), stale.id(),
                data("Concurrent change", 30, member.id()), stale.version());
        // Reproduce a read that finished before another writer committed, without timing-dependent sleeps.
        var interleaved = org.mockito.Mockito.mock(TaskOperations.class,
                org.mockito.AdditionalAnswers.delegatesTo(tasks));
        org.mockito.Mockito.doReturn(stale).when(interleaved).read(member.id(), stale.id());
        var controller = new com.example.taskmanagement.task.web.TaskController(interleaved);
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "HS256").subject(Long.toString(member.id())).build();
        var body = json.readTree("{\"version\":" + latest.version() + ",\"description\":\"Patch\"}");
        assertReason(() -> controller.patch(jwt, stale.id(), body), TaskException.Reason.VERSION_CONFLICT);
        assertThat(tasks.read(member.id(), stale.id())).isEqualTo(latest);
    }

    @Test
    void cookieTaskWritesRequireCsrfWhileReadsUseTheExistingProtectedSession() throws Exception {
        var grant = identity.login(member.username(), PASSWORD);
        var access = new jakarta.servlet.http.Cookie(
                com.example.taskmanagement.security.web.BrowserSessionCookies.ACCESS,
                tokens.issue(grant).accessToken());
        var task = create(member.id());
        String payload = "{\"version\":" + task.version() + "}";
        mvc.perform(get("/api/tasks/" + task.id()).cookie(access)).andExpect(status().isOk());
        mvc.perform(post("/api/tasks/" + task.id() + "/start").cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var csrfResult = mvc.perform(get("/api/session/csrf")).andExpect(status().isOk()).andReturn();
        var csrfBody = json.readTree(csrfResult.getResponse().getContentAsString());
        var csrfCookie = csrfResult.getResponse().getCookie(
                com.example.taskmanagement.security.web.BrowserSessionCookies.CSRF);
        mvc.perform(post("/api/tasks/" + task.id() + "/start").cookie(access, csrfCookie)
                .header(csrfBody.path("headerName").asText(), csrfBody.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void acceptsMinuteBoundariesAndMissingEstimateButRejectsOutsideRangeAtomically() {
        for (Integer estimate : new Integer[] {null, 15, 525600}) {
            var task = tasks.create(member.id(), project.id(), data("Boundary", estimate, member.id()));
            assertThat(task.estimatedMinutes()).isEqualTo(estimate);
        }
        for (int estimate : new int[] {0, 14, 525601, Integer.MAX_VALUE}) {
            var before = projects.read(owner.id(), project.id()).inputRevision();
            assertThatThrownBy(() -> tasks.create(member.id(), project.id(), data("Invalid", estimate, member.id())))
                    .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
            assertThat(projects.read(owner.id(), project.id()).inputRevision()).isEqualTo(before);
        }
        assertThat(tasks.list(owner.id(), project.id(), 0, 20).totalElements()).isEqualTo(3);
    }

    @Test
    void hidesForeignTasksAndUsesCurrentProjectMembershipAndGlobalAdminRole() throws Exception {
        var task = create(member.id());
        mvc.perform(get("/api/tasks/" + task.id()).header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        mvc.perform(get("/api/tasks/9223372036854775807").header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        var admin = identity.bootstrapAdmin(command("task_admin"));
        var running = tasks.execute(admin.id(), task.id(), Command.START, task.version());
        assertThat(running.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(tasks.read(member.id(), task.id()).status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void rejectsForeignAssigneeAndBlocksRemovalOfAnyAssignedMemberAndNonemptyProject() {
        assertReason(() -> tasks.create(owner.id(), project.id(), data("Foreign", 15, outsider.id())),
                TaskException.Reason.ASSIGNEE_NOT_MEMBER);
        var task = create(member.id());
        assertThatThrownBy(() -> projects.removeMember(owner.id(), project.id(), member.id(),
                projects.read(owner.id(), project.id()).version())).isInstanceOf(ProjectException.class)
                .extracting("reason").isEqualTo(ProjectException.Reason.MEMBER_HAS_ASSIGNMENTS);
        assertThatThrownBy(() -> projects.deleteEmpty(owner.id(), project.id(),
                projects.read(owner.id(), project.id()).version())).isInstanceOf(ProjectException.class)
                .extracting("reason").isEqualTo(ProjectException.Reason.PROJECT_NOT_EMPTY);
        tasks.replace(owner.id(), task.id(), data("Unassigned", 15, null), task.version());
        projects.removeMember(owner.id(), project.id(), member.id(), projects.read(owner.id(), project.id()).version());
        assertReason(() -> tasks.read(member.id(), task.id()), TaskException.Reason.TASK_NOT_FOUND);
    }

    @Test
    void enforcesOwnerAndAssigneeExecutionRightsIncludingOwnerOnlyCancelAndDelete() {
        var task = create(owner.id());
        assertReason(() -> tasks.execute(member.id(), task.id(), Command.START, task.version()),
                TaskException.Reason.FORBIDDEN);
        assertReason(() -> tasks.delete(member.id(), task.id(), task.version()), TaskException.Reason.FORBIDDEN);
        var assigned = tasks.replace(member.id(), task.id(), data("Assigned", 15, member.id()), task.version());
        var running = tasks.execute(member.id(), task.id(), Command.START, assigned.version());
        assertReason(() -> tasks.execute(member.id(), task.id(), Command.CANCEL, running.version()),
                TaskException.Reason.FORBIDDEN);
        var cancelled = tasks.execute(owner.id(), task.id(), Command.CANCEL, running.version());
        assertThat(cancelled.status()).isEqualTo(TaskStatus.CANCELLED);
        assertReason(() -> tasks.execute(owner.id(), task.id(), Command.RETRY, cancelled.version()),
                TaskException.Reason.INVALID_TASK_TRANSITION);
    }

    @Test
    void startRequiresAssignmentAndAssigneeCannotChangeDuringOrAfterSuccessfulExecution() {
        var unassigned = create(null);
        assertReason(() -> tasks.execute(owner.id(), unassigned.id(), Command.START, unassigned.version()),
                TaskException.Reason.ASSIGNEE_REQUIRED);
        var task = create(member.id());
        var running = tasks.execute(member.id(), task.id(), Command.START, task.version());
        assertReason(() -> tasks.replace(owner.id(), task.id(), data("Switch", 15, owner.id()), running.version()),
                TaskException.Reason.ASSIGNMENT_CHANGE_FORBIDDEN);
        var completed = tasks.execute(member.id(), task.id(), Command.COMPLETE, running.version());
        assertReason(() -> tasks.replace(owner.id(), task.id(), data("Switch", 15, null), completed.version()),
                TaskException.Reason.ASSIGNMENT_CHANGE_FORBIDDEN);
    }

    @Test
    void persistsFailedAttemptAcrossRetryReassignmentAndCompletionWithUtcFacts() {
        var task = create(member.id());
        var running = tasks.execute(member.id(), task.id(), Command.START, task.version());
        var failed = tasks.execute(member.id(), task.id(), Command.FAIL, running.version());
        var firstAttempt = tasks.attempts(owner.id(), task.id(), 0, 20).content().getFirst();
        var assigned = tasks.replace(owner.id(), task.id(), data("Reassigned", 15, owner.id()), failed.version());
        var retried = tasks.execute(owner.id(), task.id(), Command.RETRY, assigned.version());
        assertReason(() -> tasks.delete(owner.id(), task.id(), retried.version()),
                TaskException.Reason.TASK_DELETE_FORBIDDEN);
        var second = tasks.execute(owner.id(), task.id(), Command.START, retried.version());
        var completed = tasks.execute(owner.id(), task.id(), Command.COMPLETE, second.version());
        assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.attemptCount()).isEqualTo(2);
        assertThat(completed.actualEnd()).isAfterOrEqualTo(completed.actualStart());
        var history = tasks.attempts(owner.id(), task.id(), 0, 20);
        assertThat(history.content()).hasSize(2);
        assertThat(history.content().getLast()).isEqualTo(firstAttempt);
        assertThat(history.content().getFirst().outcome()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(history.content().getLast().outcome()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void cancellationAfterFailureRetainsFailedOutcomeAndNeverStartedCancellationHasNoAttempt() {
        var task = create(member.id());
        var running = tasks.execute(member.id(), task.id(), Command.START, task.version());
        var failed = tasks.execute(member.id(), task.id(), Command.FAIL, running.version());
        tasks.execute(owner.id(), task.id(), Command.CANCEL, failed.version());
        assertThat(tasks.attempts(owner.id(), task.id(), 0, 20).content().getFirst().outcome())
                .isEqualTo(TaskStatus.FAILED);
        var pending = create(null);
        var cancelled = tasks.execute(owner.id(), pending.id(), Command.CANCEL, pending.version());
        assertThat(cancelled.attemptCount()).isZero();
        assertThat(cancelled.actualStart()).isNull();
        assertThat(tasks.attempts(owner.id(), pending.id(), 0, 20).content()).isEmpty();
    }

    @Test
    void realDependenciesBlockStartUntilAllPredecessorsCompletedAndPreventReferencedDeletion() {
        var predecessor = create(owner.id());
        var task = create(member.id());
        edge(task, predecessor);
        assertReason(() -> tasks.execute(member.id(), task.id(), Command.START, task.version()),
                TaskException.Reason.DEPENDENCIES_NOT_COMPLETED);
        assertReason(() -> tasks.delete(owner.id(), predecessor.id(), predecessor.version()),
                TaskException.Reason.TASK_HAS_DEPENDENTS);
        var running = tasks.execute(owner.id(), predecessor.id(), Command.START, predecessor.version());
        var failed = tasks.execute(owner.id(), predecessor.id(), Command.FAIL, running.version());
        assertReason(() -> tasks.execute(member.id(), task.id(), Command.START, task.version()),
                TaskException.Reason.DEPENDENCIES_NOT_COMPLETED);
        var retried = tasks.execute(owner.id(), predecessor.id(), Command.RETRY, failed.version());
        var restarted = tasks.execute(owner.id(), predecessor.id(), Command.START, retried.version());
        tasks.execute(owner.id(), predecessor.id(), Command.COMPLETE, restarted.version());
        assertThat(tasks.execute(member.id(), task.id(), Command.START, task.version()).status())
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void deletesOnlyNeverStartedTaskAndItsIncomingEdgesWithoutDeletingOtherTasks() {
        var predecessor = create(owner.id());
        var task = create(member.id());
        edge(task, predecessor);
        tasks.delete(owner.id(), task.id(), task.version());
        assertReason(() -> tasks.read(owner.id(), task.id()), TaskException.Reason.TASK_NOT_FOUND);
        assertThat(tasks.read(owner.id(), predecessor.id()).id()).isEqualTo(predecessor.id());
        assertThat(jdbc.queryForObject("select count(*) from task_management.task_dependencies", Integer.class))
                .isZero();
        tasks.delete(owner.id(), predecessor.id(), predecessor.version());
        projects.deleteEmpty(owner.id(), project.id(), projects.read(owner.id(), project.id()).version());
    }

    @Test
    void staleVersionDoesNotOverwriteMetadataOrCreateAdditionalAttempt() {
        var task = create(member.id());
        var changed = tasks.replace(member.id(), task.id(), data("Changed", 525600, member.id()), task.version());
        assertReason(() -> tasks.replace(owner.id(), task.id(), data("Lost", 15, owner.id()), task.version()),
                TaskException.Reason.VERSION_CONFLICT);
        assertReason(() -> tasks.execute(member.id(), task.id(), Command.START, task.version()),
                TaskException.Reason.VERSION_CONFLICT);
        assertThat(tasks.read(owner.id(), task.id()).name()).isEqualTo("Changed");
        assertThat(tasks.attempts(owner.id(), task.id(), 0, 20).totalElements()).isZero();
        assertThat(changed.estimatedMinutes()).isEqualTo(525600);
    }

    @Test
    void bothCreateAndListRoutesUseSameContractAndRejectConflictingProjectIds() throws Exception {
        var payload = Map.of("projectId", project.id(), "name", "API task", "priority", "HIGH",
                "estimatedMinutes", 15, "assigneeId", member.id());
        var created = mvc.perform(post("/api/tasks").header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.estimatedMinutes").value(15)).andReturn();
        long taskId = json.readTree(created.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(get("/api/projects/" + project.id() + "/tasks").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(taskId));
        mvc.perform(get("/api/tasks").param("projectId", Long.toString(project.id()))
                .header("Authorization", bearer(member))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/projects/" + (project.id() + 1) + "/tasks")
                .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/" + project.id() + "/tasks")
                .param("projectId", Long.toString(project.id() + 1)).header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchRetainsOmittedFieldsClearsOptionalFieldsAndRejectsLifecycleAndProjectChanges() throws Exception {
        var task = create(member.id());
        String auth = bearer(member);
        mvc.perform(patch("/api/tasks/" + task.id()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + task.version() + ",\"estimatedMinutes\":null,\"assigneeId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(task.name()))
                .andExpect(jsonPath("$.estimatedMinutes").doesNotExist())
                .andExpect(jsonPath("$.assigneeId").doesNotExist());
        var current = tasks.read(member.id(), task.id());
        for (var forbidden : List.of("\"status\":\"COMPLETED\"", "\"actualEnd\":\"2026-10-14T10:00:00Z\"",
                "\"projectId\":" + (project.id() + 1), "\"estimatedMinutes\":15.5")) {
            mvc.perform(patch("/api/tasks/" + task.id()).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":" + current.version() + "," + forbidden + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(tasks.read(member.id(), task.id()).status()).isEqualTo(TaskStatus.NOT_STARTED);
        mvc.perform(put("/api/tasks/" + task.id()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + current.version() + ",\"name\":\"Replaced\",\"priority\":\"LOW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Replaced"));
    }

    @Test
    void exposesCommandsAndHistoryAndRejectsInvalidEstimatePagingAndMissingVersion() throws Exception {
        var task = create(member.id());
        String auth = bearer(member);
        mvc.perform(post("/api/tasks/" + task.id() + "/start").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + task.version() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptCount").value(1));
        mvc.perform(get("/api/tasks/" + task.id() + "/attempts").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].number").value(1));
        mvc.perform(post("/api/tasks/" + task.id() + "/complete").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks/" + task.id() + "/attempts").param("size", "101")
                .header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":" + project.id()
                        + ",\"name\":\"Bad\",\"priority\":\"HIGH\",\"estimatedMinutes\":14}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(delete("/api/tasks/" + task.id()).param("version", "1").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks/" + task.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentStartCreatesExactlyOneAttempt() throws Exception {
        var task = create(member.id());
        var latch = new CountDownLatch(1);
        var outcomes = ConcurrentCalls.race(20, List.of(TaskException.class),
                () -> {
                    ConcurrentCalls.await(latch);
                    return tasks.execute(member.id(), task.id(), Command.START, task.version());
                },
                () -> {
                    latch.countDown();
                    return tasks.execute(owner.id(), task.id(), Command.START, task.version());
                });
        assertThat(outcomes.stream().filter(TaskView.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(TaskException.class::isInstance).map(TaskException.class::cast)
                .map(TaskException::reason).toList()).containsExactly(TaskException.Reason.VERSION_CONFLICT);
        assertThat(tasks.attempts(owner.id(), task.id(), 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void concurrentAssignmentAndMemberRemovalPreserveMembershipInvariant() throws Exception {
        var task = create(null);
        var revision = projects.read(owner.id(), project.id()).version();
        var outcomes = ConcurrentCalls.race(20, List.of(TaskException.class, ProjectException.class),
                () -> tasks.replace(member.id(), task.id(), data("Assigned", 15, member.id()), task.version()),
                () -> projects.removeMember(owner.id(), project.id(), member.id(), revision));
        assertThat(outcomes.stream().filter(RuntimeException.class::isInstance).count()).isEqualTo(1);
        Long assignee = tasks.read(owner.id(), task.id()).assigneeId();
        int membership = jdbc.queryForObject(
                "select count(*) from task_management.project_members where project_id = ? and user_id = ?",
                Integer.class, project.id(), member.id());
        assertThat(assignee == null || membership == 1).isTrue();
    }

    @Test
    void taskWriterRechecksAccessAfterWaitingForMembershipLock() throws Exception {
        var task = create(null);
        long currentProjectVersion = projects.read(owner.id(), project.id()).version();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var removal = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                coordination.lockAndAuthorize(owner.id(), project.id(), ProjectCoordination.Permission.MANAGE_PROJECT);
                locked.countDown();
                ConcurrentCalls.await(release);
                return projects.removeMember(owner.id(), project.id(), member.id(), currentProjectVersion);
            }));
            ConcurrentCalls.await(locked);
            var write = executor.submit(ConcurrentCalls.capture(() -> tasks.replace(member.id(), task.id(),
                    data("Denied", 15, null), task.version()), List.of(TaskException.class)));
            release.countDown();
            removal.get(20, TimeUnit.SECONDS);
            assertThat(write.get(20, TimeUnit.SECONDS)).isInstanceOf(TaskException.class)
                    .extracting("reason").isEqualTo(TaskException.Reason.TASK_NOT_FOUND);
        } finally {
            release.countDown();
        }
        assertThat(tasks.read(owner.id(), task.id()).name()).isEqualTo(task.name());
    }

    @Test
    void databaseRejectsCrossProjectEdgesSelfEdgesForeignAssignmentsAndInvalidEstimates() {
        var task = create(member.id());
        var otherProject = projects.create(owner.id(), new ProjectData("Other", "",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "UTC"));
        var otherTask = tasks.create(owner.id(), otherProject.id(), data("Other", 15, null));
        assertThatThrownBy(() -> edge(task, otherTask)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> edge(task, task)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update task_management.tasks set estimated_minutes = 14 where id = ?",
                task.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update task_management.tasks set assignee_id = ? where id = ?",
                outsider.id(), task.id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preservesFinitePostgresqlDateBoundsAndRejectsOutsideWithoutMutation() {
        for (var date : List.of(LocalDate.of(-4713, 11, 24), LocalDate.of(5874897, 12, 31))) {
            var task = tasks.create(owner.id(), project.id(),
                    new TaskData("Date", "", TaskPriority.LOW, 15, date, null));
            assertThat(tasks.read(owner.id(), task.id()).dueDate()).isEqualTo(date);
        }
        assertReason(() -> tasks.create(owner.id(), project.id(),
                new TaskData("Invalid", "", TaskPriority.LOW, 15, LocalDate.of(5874898, 1, 1), null)),
                TaskException.Reason.VALIDATION_ERROR);
    }

    @Test
    void deletesTaskCancelledBeforeAnyStartButPreservesCancellationAfterExecution() {
        var pending = create(null);
        var cancelled = tasks.execute(owner.id(), pending.id(), Command.CANCEL, pending.version());
        tasks.delete(owner.id(), cancelled.id(), cancelled.version());
        assertReason(() -> tasks.read(owner.id(), cancelled.id()), TaskException.Reason.TASK_NOT_FOUND);
        var started = create(owner.id());
        var running = tasks.execute(owner.id(), started.id(), Command.START, started.version());
        var stopped = tasks.execute(owner.id(), started.id(), Command.CANCEL, running.version());
        assertReason(() -> tasks.delete(owner.id(), stopped.id(), stopped.version()),
                TaskException.Reason.TASK_DELETE_FORBIDDEN);
        assertThat(tasks.attempts(owner.id(), stopped.id(), 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void startWaitsForGraphWriterAndReadsCommittedPredecessorFacts() throws Exception {
        var predecessor = create(owner.id());
        var task = create(member.id());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var graphWrite = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                coordination.lockAndAuthorize(owner.id(), project.id(), ProjectCoordination.Permission.WRITE_TASKS);
                edge(task, predecessor);
                locked.countDown();
                ConcurrentCalls.await(release);
                coordination.markInputChanged(owner.id(), project.id());
                return true;
            }));
            ConcurrentCalls.await(locked);
            var start = executor.submit(ConcurrentCalls.capture(() -> tasks.execute(member.id(), task.id(),
                    Command.START, task.version()), List.of(TaskException.class)));
            release.countDown();
            graphWrite.get(20, TimeUnit.SECONDS);
            assertThat(start.get(20, TimeUnit.SECONDS)).isInstanceOf(TaskException.class)
                    .extracting("reason").isEqualTo(TaskException.Reason.DEPENDENCIES_NOT_COMPLETED);
        } finally {
            release.countDown();
        }
        assertThat(tasks.attempts(owner.id(), task.id(), 0, 20).totalElements()).isZero();
    }

    private UserView register(String name) {
        return identity.register(command(name));
    }

    private static RegisterUser command(String name) {
        return new RegisterUser(name, name + "@example.com", PASSWORD, "First", "Last");
    }

    private TaskView create(Long assignee) {
        return tasks.create(owner.id(), project.id(), data("Task", 15, assignee));
    }

    private static TaskData data(String name, Integer estimate, Long assignee) {
        return new TaskData(name, "Description", TaskPriority.HIGH, estimate, LocalDate.of(2026, 12, 31), assignee);
    }

    private void edge(TaskView task, TaskView predecessor) {
        jdbc.update("insert into task_management.task_dependencies(project_id, task_id, depends_on_task_id)"
                + " values (?, ?, ?)", task.projectId(), task.id(), predecessor.id());
    }

    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(identity.login(user.username(), PASSWORD)).accessToken();
    }

    private static void assertReason(Runnable action, TaskException.Reason reason) {
        assertThatThrownBy(action::run).isInstanceOf(TaskException.class).extracting("reason").isEqualTo(reason);
    }
}
