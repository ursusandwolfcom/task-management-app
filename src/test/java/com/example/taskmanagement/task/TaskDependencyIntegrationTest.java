package com.example.taskmanagement.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.planning.core.GraphValidationException;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.ConcurrentCalls;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskDependencyOperations;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPlanningOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.application.TaskDependencyService;
import com.example.taskmanagement.task.application.TaskGraphLoader;
import com.example.taskmanagement.task.application.TaskProjectAccess;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import com.example.taskmanagement.task.domain.TaskStatus;
import com.example.taskmanagement.task.persistence.DependencyRepository;
import com.example.taskmanagement.task.persistence.TaskRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class TaskDependencyIntegrationTest {
    private static final String PASSWORD = "dependency-test-passphrase";
    @Autowired
    private TaskDependencyOperations dependencies;
    @Autowired
    private TaskPlanningOperations planning;
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
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private TaskProjectAccess access;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private DependencyRepository dependencyRepository;
    @Autowired
    private EntityManager entityManager;
    private UserView owner;
    private UserView member;
    private UserView outsider;
    private ProjectView project;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        owner = identity.register(command("dependency_owner"));
        member = identity.register(command("dependency_member"));
        outsider = identity.register(command("dependency_outsider"));
        project = projects.create(owner.id(), data());
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
    }

    @Test
    void participantAddsAndRemovesEdgeWithOneRevisionIncrementAndNoTaskOrHistoryChanges() throws Exception {
        var predecessor = create();
        var successor = create();
        long before = revision();
        String auth = bearer(member);
        mvc.perform(post(route(successor)).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(body(predecessor.id(), before)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.inputRevision").value(before + 1))
                .andExpect(header().string("Location", route(successor) + "/" + predecessor.id()))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(edgeCount()).isEqualTo(1);
        assertThat(planning.availableTasks(member.id(), project.id()).tasks())
                .extracting(TaskView::id).containsExactly(predecessor.id());
        assertThat(tasks.read(owner.id(), successor.id())).isEqualTo(successor);
        assertThat(tasks.attempts(owner.id(), successor.id(), 0, 20).totalElements()).isZero();
        mvc.perform(delete(route(successor) + "/" + predecessor.id()).header("Authorization", auth)
                .param("inputRevision", Long.toString(revision())))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Project-Input-Revision", Long.toString(before + 2)));
        assertThat(edgeCount()).isZero();
        assertThat(tasks.read(owner.id(), successor.id())).isEqualTo(successor);
    }

    @Test
    void duplicateSelfMissingEdgeAndStaleRevisionDoNotPartiallyChangeGraph() throws Exception {
        var predecessor = create();
        var successor = create();
        long before = revision();
        dependencies.add(member.id(), successor.id(), predecessor.id(), before);
        long current = revision();
        assertReason(() -> dependencies.add(member.id(), successor.id(), predecessor.id(), current),
                TaskException.Reason.DEPENDENCY_ALREADY_EXISTS);
        assertReason(() -> dependencies.remove(member.id(), successor.id(), predecessor.id(), before),
                TaskException.Reason.VERSION_CONFLICT);
        assertReason(() -> dependencies.remove(member.id(), predecessor.id(), successor.id(), current),
                TaskException.Reason.DEPENDENCY_NOT_FOUND);
        mvc.perform(post(route(successor)).header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content(body(successor.id(), current)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SELF_DEPENDENCY"));
        assertThat(revision()).isEqualTo(current);
        assertThat(edgeCount()).isEqualTo(1);
    }

    @Test
    void cycleIsRejectedBeforeInsertAndDoesNotAdvanceRevision() throws Exception {
        var first = create();
        var second = create();
        var third = create();
        dependencies.add(member.id(), second.id(), first.id(), revision());
        dependencies.add(member.id(), third.id(), second.id(), revision());
        long before = revision();
        mvc.perform(post(route(first)).header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content(body(third.id(), before)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CYCLIC_DEPENDENCY"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(edgeCount()).isEqualTo(2);
        assertThat(revision()).isEqualTo(before);
        assertThat(planning.executionOrder(member.id(), project.id()).taskIds())
                .containsExactly(first.id(), second.id(), third.id());
    }

    @Test
    void rejectsEveryStartedOrTerminalSuccessorIncludingNotStartedAfterRetry() {
        var predecessor = create();
        var runningPredecessor = tasks.execute(owner.id(), predecessor.id(), Command.START, predecessor.version());
        tasks.execute(owner.id(), predecessor.id(), Command.COMPLETE, runningPredecessor.version());
        for (var state : TaskStatus.values()) {
            var successor = create();
            dependencies.add(owner.id(), successor.id(), predecessor.id(), revision());
            if (state != TaskStatus.CANCELLED) {
                successor = tasks.execute(owner.id(), successor.id(), Command.START, successor.version());
            }
            successor = switch (state) {
                case IN_PROGRESS -> successor;
                case COMPLETED -> tasks.execute(owner.id(), successor.id(), Command.COMPLETE, successor.version());
                case CANCELLED -> tasks.execute(owner.id(), successor.id(), Command.CANCEL, successor.version());
                case FAILED, NOT_STARTED -> tasks.execute(owner.id(), successor.id(),
                        Command.FAIL, successor.version());
            };
            if (state == TaskStatus.NOT_STARTED) {
                successor = tasks.execute(owner.id(), successor.id(), Command.RETRY, successor.version());
            }
            var snapshot = successor;
            long before = revision();
            assertReason(() -> dependencies.add(owner.id(), snapshot.id(), predecessor.id(), before),
                    TaskException.Reason.DEPENDENCY_CHANGE_FORBIDDEN);
            assertReason(() -> dependencies.remove(owner.id(), snapshot.id(), predecessor.id(), before),
                    TaskException.Reason.DEPENDENCY_CHANGE_FORBIDDEN);
            assertThat(revision()).isEqualTo(before);
            assertThat(tasks.read(owner.id(), successor.id())).isEqualTo(snapshot);
        }
    }

    @Test
    void completedPredecessorAllowsStartButFailedPredecessorKeepsNewSuccessorBlocked() {
        var predecessor = create();
        var running = tasks.execute(owner.id(), predecessor.id(), Command.START, predecessor.version());
        var failed = tasks.execute(owner.id(), predecessor.id(), Command.FAIL, running.version());
        var successor = create();
        dependencies.add(member.id(), successor.id(), predecessor.id(), revision());
        assertReason(() -> tasks.execute(owner.id(), successor.id(), Command.START, successor.version()),
                TaskException.Reason.DEPENDENCIES_NOT_COMPLETED);
        var retry = tasks.execute(owner.id(), predecessor.id(), Command.RETRY, failed.version());
        var again = tasks.execute(owner.id(), predecessor.id(), Command.START, retry.version());
        tasks.execute(owner.id(), predecessor.id(), Command.COMPLETE, again.version());
        assertThat(tasks.execute(owner.id(), successor.id(), Command.START, successor.version()).status())
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void crossProjectErrorRequiresReadablePredecessorAndForeignTargetsRemainHidden() throws Exception {
        var successor = create();
        var shared = projects.create(owner.id(), data());
        var visible = tasks.create(owner.id(), shared.id(), taskData());
        var secret = projects.create(outsider.id(), data());
        var hidden = tasks.create(outsider.id(), secret.id(),
                new TaskData("Hidden", "", TaskPriority.HIGH, 15, null, outsider.id(), TaskBucket.WORK));
        assertReason(() -> dependencies.add(owner.id(), successor.id(), visible.id(), revision()),
                TaskException.Reason.CROSS_PROJECT_DEPENDENCY);
        assertReason(() -> dependencies.add(member.id(), successor.id(), visible.id(), revision()),
                TaskException.Reason.TASK_NOT_FOUND);
        assertReason(() -> dependencies.add(owner.id(), successor.id(), hidden.id(), revision()),
                TaskException.Reason.TASK_NOT_FOUND);
        assertReason(() -> dependencies.add(owner.id(), successor.id(), Long.MAX_VALUE, revision()),
                TaskException.Reason.TASK_NOT_FOUND);
        mvc.perform(post(route(successor)).header("Authorization", bearer(outsider))
                .contentType(MediaType.APPLICATION_JSON).content(body(visible.id(), revision())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        assertThat(edgeCount()).isZero();
        var admin = identity.bootstrapAdmin(command("dependency_admin"));
        var predecessor = create();
        assertThat(dependencies.add(admin.id(), successor.id(), predecessor.id(), revision()).taskId())
                .isEqualTo(successor.id());
    }

    @Test
    void strictDependencyBodyRequiresExactPositiveIdsAndExplicitNonnegativeRevision() throws Exception {
        var successor = create();
        String auth = bearer(member);
        for (String invalid : List.of("{}", "null", "[]", "{\"dependsOnTaskId\":1}",
                "{\"dependsOnTaskId\":0,\"inputRevision\":0}",
                "{\"dependsOnTaskId\":1,\"inputRevision\":-1}",
                "{\"dependsOnTaskId\":1.5,\"inputRevision\":0}",
                "{\"dependsOnTaskId\":\"1\",\"inputRevision\":0}",
                "{\"dependsOnTaskId\":1,\"inputRevision\":null}",
                "{\"dependsOnTaskId\":9223372036854775808,\"inputRevision\":0}",
                "{\"dependsOnTaskId\":1,\"inputRevision\":0,\"status\":\"COMPLETED\"}")) {
            mvc.perform(post(route(successor)).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(edgeCount()).isZero();
    }

    @Test
    void cookieDependencyWritesRequireCsrfAndThenUseSameService() throws Exception {
        var predecessor = create();
        var successor = create();
        var grant = identity.login(member.username(), PASSWORD);
        var accessCookie = new jakarta.servlet.http.Cookie(
                com.example.taskmanagement.security.web.BrowserSessionCookies.ACCESS,
                tokens.issue(grant).accessToken());
        mvc.perform(post(route(successor)).cookie(accessCookie).contentType(MediaType.APPLICATION_JSON)
                .content(body(predecessor.id(), revision())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var csrfResult = mvc.perform(get("/api/session/csrf")).andReturn();
        var json = new tools.jackson.databind.ObjectMapper().readTree(csrfResult.getResponse().getContentAsString());
        var csrfCookie = csrfResult.getResponse().getCookie(
                com.example.taskmanagement.security.web.BrowserSessionCookies.CSRF);
        mvc.perform(post(route(successor)).cookie(accessCookie, csrfCookie)
                .header(json.path("headerName").asText(), json.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content(body(predecessor.id(), revision())))
                .andExpect(status().isCreated());
    }

    @Test
    void oppositeConcurrentEdgesCommitAtMostOneAndPreserveDag() throws Exception {
        var first = create();
        var second = create();
        long expected = revision();
        var outcomes = ConcurrentCalls.race(20, List.of(TaskException.class, GraphValidationException.class),
                () -> dependencies.add(member.id(), second.id(), first.id(), expected),
                () -> dependencies.add(owner.id(), first.id(), second.id(), expected));
        assertThat(outcomes.stream().filter(TaskDependencyOperations.Change.class::isInstance).count()).isEqualTo(1);
        assertThat(edgeCount()).isEqualTo(1);
        assertThat(revision()).isEqualTo(expected + 1);
        assertThat(planning.executionOrder(owner.id(), project.id()).taskIds()).hasSize(2);
    }

    @Test
    void startRacingWithIncompleteDependencyCannotBothSucceed() throws Exception {
        var predecessor = create();
        var successor = create();
        long expected = revision();
        var outcomes = ConcurrentCalls.race(20, List.of(TaskException.class),
                () -> dependencies.add(member.id(), successor.id(), predecessor.id(), expected),
                () -> tasks.execute(owner.id(), successor.id(), Command.START, successor.version()));
        assertThat(outcomes.stream().filter(TaskException.class::isInstance).count()).isEqualTo(1);
        var current = tasks.read(owner.id(), successor.id());
        assertThat(current.status() == TaskStatus.NOT_STARTED || edgeCount() == 0).isTrue();
        assertThat(revision()).isEqualTo(expected + 1);
    }

    @Test
    void deletionRacingWithDependencyNeverCreatesDanglingReference() throws Exception {
        var predecessor = create();
        var successor = create();
        long expected = revision();
        var outcomes = ConcurrentCalls.race(20, List.of(TaskException.class),
                () -> dependencies.add(member.id(), successor.id(), predecessor.id(), expected),
                () -> {
                    tasks.delete(owner.id(), predecessor.id(), predecessor.version());
                    return Boolean.TRUE;
                });
        assertThat(outcomes.stream().filter(TaskException.class::isInstance).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from task_management.task_dependencies d "
                + "left join task_management.tasks t on t.id = d.depends_on_task_id where t.id is null", Long.class))
                .isZero();
    }

    @Test
    void waitingDependencyWriterRechecksMembershipAfterRemoval() throws Exception {
        var predecessor = create();
        var successor = create();
        long expected = revision();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        long version = projects.read(owner.id(), project.id()).version();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var removal = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                coordination.lockAndAuthorize(owner.id(), project.id(), ProjectCoordination.Permission.MANAGE_PROJECT);
                locked.countDown();
                ConcurrentCalls.await(release);
                return projects.removeMember(owner.id(), project.id(), member.id(), version);
            }));
            ConcurrentCalls.await(locked);
            var writer = executor.submit(ConcurrentCalls.capture(
                    () -> dependencies.add(member.id(), successor.id(), predecessor.id(), expected),
                    List.of(TaskException.class)));
            release.countDown();
            removal.get(20, TimeUnit.SECONDS);
            assertThat(writer.get(20, TimeUnit.SECONDS)).isInstanceOf(TaskException.class)
                    .extracting("reason").isEqualTo(TaskException.Reason.TASK_NOT_FOUND);
        } finally {
            release.countDown();
        }
        assertThat(edgeCount()).isZero();
    }

    @Test
    void callerRollbackRestoresBothJdbcEdgesAndJpaRevision() {
        var predecessor = create();
        var successor = create();
        long before = revision();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            dependencies.add(owner.id(), successor.id(), predecessor.id(), before);
            status.setRollbackOnly();
        });
        assertThat(edgeCount()).isZero();
        assertThat(revision()).isEqualTo(before);
        dependencies.add(owner.id(), successor.id(), predecessor.id(), before);
        transaction.executeWithoutResult(status -> {
            dependencies.remove(owner.id(), successor.id(), predecessor.id(), before + 1);
            status.setRollbackOnly();
        });
        assertThat(edgeCount()).isEqualTo(1);
        assertThat(revision()).isEqualTo(before + 1);
    }

    @Test
    void dependencyGuardRefreshesTaskAlreadyReadByCallersTransaction() {
        var predecessor = create();
        var successor = create();
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertReason(() -> new TransactionTemplate(transactionManager).execute(status -> {
                tasks.read(owner.id(), successor.id());
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> tasks.execute(owner.id(), successor.id(), Command.START, successor.version()), executor)
                        .orTimeout(20, TimeUnit.SECONDS).join();
                long current = jdbc.queryForObject(
                        "select input_revision from task_management.projects where id = ?", Long.class, project.id());
                return dependencies.add(owner.id(), successor.id(), predecessor.id(), current);
            }), TaskException.Reason.DEPENDENCY_CHANGE_FORBIDDEN);
        }
        assertThat(edgeCount()).isZero();
        assertThat(tasks.read(owner.id(), successor.id()).status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void edgeLimitRejectsAddAtomicallyButRemovalCanRepairOversizedGraph() {
        var first = create();
        var second = create();
        var third = create();
        var limited = new TaskDependencyService(access, taskRepository, dependencyRepository,
                new TaskGraphLoader(taskRepository, dependencyRepository, coordination, 10, 1),
                coordination, org.mockito.Mockito.mock(com.example.taskmanagement.task.api.ActivityOperations.class),
                entityManager);
        var transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> limited.add(owner.id(), second.id(), first.id(), revision()));
        long expected = revision();
        assertReason(() -> transaction.execute(status -> limited.add(owner.id(), third.id(), second.id(), expected)),
                TaskException.Reason.GRAPH_TOO_LARGE);
        assertThat(revision()).isEqualTo(expected);
        dependencyRepository.insert(project.id(), third.id(), second.id());
        transaction.execute(status -> limited.remove(owner.id(), third.id(), second.id(), expected));
        assertThat(edgeCount()).isEqualTo(1);
    }

    private TaskView create() {
        return tasks.create(owner.id(), project.id(), taskData());
    }

    private TaskData taskData() {
        return new TaskData("Task", "", TaskPriority.HIGH, 15, null, owner.id(), TaskBucket.WORK);
    }

    private static ProjectData data() {
        return new ProjectData("Project", "", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "UTC");
    }

    private long revision() {
        return projects.read(owner.id(), project.id()).inputRevision();
    }

    private long edgeCount() {
        return jdbc.queryForObject("select count(*) from task_management.task_dependencies", Long.class);
    }

    private String route(TaskView task) {
        return "/api/tasks/" + task.id() + "/dependencies";
    }

    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(identity.login(user.username(), PASSWORD)).accessToken();
    }

    private static String body(long predecessor, long revision) {
        return "{\"dependsOnTaskId\":" + predecessor + ",\"inputRevision\":" + revision + "}";
    }

    private static RegisterUser command(String name) {
        return new RegisterUser(name, name + "@example.com", PASSWORD, "First", "Last");
    }

    private static void assertReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            TaskException.Reason reason) {
        assertThatThrownBy(operation).isInstanceOf(TaskException.class).extracting("reason").isEqualTo(reason);
    }
}
