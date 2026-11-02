package com.example.taskmanagement.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.ConcurrentCalls;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPlanningOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.application.TaskGraphLoader;
import com.example.taskmanagement.task.application.TaskPlanningService;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import com.example.taskmanagement.task.persistence.DependencyRepository;
import com.example.taskmanagement.task.persistence.TaskRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class TaskPlanningIntegrationTest {
    private static final String PASSWORD = "planning-test-passphrase";
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
    private TaskRepository taskRepository;
    @Autowired
    private DependencyRepository dependencies;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private UserView owner;
    private UserView member;
    private UserView outsider;
    private ProjectView project;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        owner = identity.register(command("graph_owner"));
        member = identity.register(command("graph_member"));
        outsider = identity.register(command("graph_outsider"));
        project = projects.create(owner.id(), new ProjectData("Graph", "",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "UTC"));
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
    }

    @Test
    void emptyGraphHasEmptyOrderAndAvailableAndNoContentNextWithRevision() throws Exception {
        String auth = bearer(member);
        mvc.perform(get(route("execution-order")).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskIds").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(route("available-tasks")).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tasks").isEmpty())
                .andExpect(jsonPath("$.blockingReasons").isEmpty());
        mvc.perform(get(route("next-task")).header("Authorization", auth))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Project-Input-Revision", Long.toString(project.inputRevision())));
    }

    @Test
    void pureCoreOrdersPersistedGraphAndOnlyActualCompletionUnlocksSuccessorWithoutGetWrites() throws Exception {
        var predecessor = create("Predecessor", TaskPriority.LOW);
        var successor = create("Successor", TaskPriority.CRITICAL);
        var independent = create("Independent", TaskPriority.HIGH);
        edge(successor, predecessor);
        var revision = projects.read(owner.id(), project.id());
        assertThat(planning.executionOrder(member.id(), project.id()).taskIds())
                .containsExactly(independent.id(), predecessor.id(), successor.id());
        var available = planning.availableTasks(member.id(), project.id());
        assertThat(available.tasks()).extracting(TaskView::id).containsExactly(independent.id(), predecessor.id());
        assertThat(available.blockingReasons()).containsExactly(new TaskPlanningOperations.BlockedTask(
                successor.id(), "DEPENDENCIES_NOT_COMPLETED", List.of(predecessor.id())));
        assertThat(planning.nextTask(member.id(), project.id()).task().id()).isEqualTo(independent.id());
        assertThat(tasks.read(owner.id(), predecessor.id())).isEqualTo(predecessor);
        assertThat(projects.read(owner.id(), project.id())).isEqualTo(revision);
        assertThat(tasks.attempts(owner.id(), predecessor.id(), 0, 20).totalElements()).isZero();
        mvc.perform(get(route("available-tasks")).header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inputRevision").value(revision.inputRevision()))
                .andExpect(jsonPath("$.blockingReasons[0].predecessorIds[0]").value(predecessor.id()));
        var running = tasks.execute(owner.id(), predecessor.id(), Command.START, predecessor.version());
        assertThat(planning.availableTasks(owner.id(), project.id()).tasks())
                .extracting(TaskView::id).containsExactly(independent.id());
        tasks.execute(owner.id(), predecessor.id(), Command.COMPLETE, running.version());
        assertThat(planning.availableTasks(owner.id(), project.id()).tasks())
                .extracting(TaskView::id).containsExactly(successor.id(), independent.id());
        mvc.perform(get(route("next-task")).header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.task.id").value(successor.id()));
    }

    @Test
    void diamondRequiresBothActualPredecessorCompletions() {
        var root = create("Root", TaskPriority.LOW);
        var left = create("Left", TaskPriority.MEDIUM);
        var right = create("Right", TaskPriority.HIGH);
        var sink = create("Sink", TaskPriority.CRITICAL);
        edge(left, root);
        edge(right, root);
        edge(sink, left);
        edge(sink, right);
        for (var task : List.of(root, left)) {
            var running = tasks.execute(owner.id(), task.id(), Command.START, task.version());
            tasks.execute(owner.id(), task.id(), Command.COMPLETE, running.version());
        }
        var available = planning.availableTasks(member.id(), project.id());
        assertThat(available.tasks()).extracting(TaskView::id).containsExactly(right.id());
        assertThat(available.blockingReasons().getFirst().predecessorIds()).containsExactly(right.id());
        var running = tasks.execute(owner.id(), right.id(), Command.START, right.version());
        tasks.execute(owner.id(), right.id(), Command.COMPLETE, running.version());
        assertThat(planning.nextTask(member.id(), project.id()).task().id()).isEqualTo(sink.id());
    }

    @Test
    void failedAndCancelledPredecessorsRemainBlockersAndAreNotCandidates() {
        for (var finish : List.of(Command.FAIL, Command.CANCEL)) {
            var predecessor = create("Blocked by " + finish, TaskPriority.HIGH);
            var successor = create("Successor " + finish, TaskPriority.LOW);
            edge(successor, predecessor);
            var running = tasks.execute(owner.id(), predecessor.id(), Command.START, predecessor.version());
            tasks.execute(owner.id(), predecessor.id(), finish, running.version());
            var available = planning.availableTasks(member.id(), project.id());
            assertThat(available.tasks()).isEmpty();
            assertThat(available.blockingReasons()).anySatisfy(blocked -> {
                assertThat(blocked.taskId()).isEqualTo(successor.id());
                assertThat(blocked.predecessorIds()).containsExactly(predecessor.id());
            });
            assertThat(planning.nextTask(member.id(), project.id()).task()).isNull();
        }
    }

    @Test
    void dueDateAndIdBreakPriorityTiesIncludingFinitePostgresqlBounds() {
        var late = tasks.create(owner.id(), project.id(), new TaskData("Late", "", TaskPriority.HIGH,
                null, LocalDate.of(5874897, 12, 31), null, TaskBucket.WORK));
        var first = tasks.create(owner.id(), project.id(), new TaskData("First", "", TaskPriority.HIGH,
                null, LocalDate.of(-4713, 11, 24), null, TaskBucket.WORK));
        var second = tasks.create(owner.id(), project.id(), new TaskData("Second", "", TaskPriority.HIGH,
                null, LocalDate.of(-4713, 11, 24), null, TaskBucket.WORK));
        var noDate = tasks.create(owner.id(), project.id(), new TaskData("No date", "", TaskPriority.HIGH,
                null, null, null, TaskBucket.WORK));
        assertThat(planning.availableTasks(member.id(), project.id()).tasks()).extracting(TaskView::id)
                .containsExactly(first.id(), second.id(), late.id(), noDate.id());
        assertThat(planning.nextTask(member.id(), project.id()).task().id()).isEqualTo(first.id());
    }

    @Test
    void graphEndpointsHideForeignProjectsUseLiveMembershipAndAllowAdmin() throws Exception {
        var task = create("Task", TaskPriority.HIGH);
        for (String endpoint : List.of("execution-order", "available-tasks", "next-task")) {
            mvc.perform(get(route(endpoint))).andExpect(status().isUnauthorized());
            mvc.perform(get(route(endpoint)).header("Authorization", bearer(outsider)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
            mvc.perform(get("/api/projects/9223372036854775807/" + endpoint)
                    .header("Authorization", bearer(outsider)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        }
        String revoked = bearer(member);
        projects.removeMember(owner.id(), project.id(), member.id(), projects.read(owner.id(), project.id()).version());
        mvc.perform(get(route("available-tasks")).header("Authorization", revoked)).andExpect(status().isNotFound());
        var admin = identity.bootstrapAdmin(command("graph_admin"));
        assertThat(planning.nextTask(admin.id(), project.id()).task().id()).isEqualTo(task.id());
    }

    @Test
    void detectsPersistedCycleWithoutMutatingFactsAndDoesNotCacheError() throws Exception {
        var first = create("First", TaskPriority.HIGH);
        var second = create("Second", TaskPriority.LOW);
        edge(first, second);
        edge(second, first);
        for (String endpoint : List.of("execution-order", "available-tasks", "next-task")) {
            mvc.perform(get(route(endpoint)).header("Authorization", bearer(member)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CYCLIC_DEPENDENCY"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        assertThat(tasks.read(owner.id(), first.id())).isEqualTo(first);
    }

    @Test
    void refusesOversizedGraphRatherThanReturningPartialDataAndAuthorizesBeforeLimits() {
        var first = create("First", TaskPriority.HIGH);
        var second = create("Second", TaskPriority.LOW);
        var third = create("Third", TaskPriority.MEDIUM);
        edge(second, first);
        edge(third, second);
        for (int[] limits : new int[][] {{2, 10}, {10, 1}}) {
            var limited = new TaskPlanningService(new TaskGraphLoader(taskRepository, dependencies, coordination,
                    limits[0], limits[1]));
            var transaction = new TransactionTemplate(transactionManager);
            assertThatThrownBy(() -> transaction.execute(status -> limited.executionOrder(member.id(), project.id())))
                    .isInstanceOf(TaskException.class).extracting("reason")
                    .isEqualTo(TaskException.Reason.GRAPH_TOO_LARGE);
            assertThatThrownBy(() -> transaction.execute(status -> limited.executionOrder(outsider.id(), project.id())))
                    .isInstanceOf(ProjectException.class).extracting("reason")
                    .isEqualTo(ProjectException.Reason.PROJECT_NOT_FOUND);
        }
        assertThat(planning.executionOrder(owner.id(), project.id()).taskIds()).hasSize(3);
    }

    @Test
    void planningCannotAccidentallyRunInsideCallersWriteTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .execute(status -> planning.executionOrder(member.id(), project.id())))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void taskProjectionQueryCountDoesNotGrowWithGraphSize() {
        create("First", TaskPriority.HIGH);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            planning.availableTasks(member.id(), project.id());
            long small = statistics.getPrepareStatementCount();
            for (int index = 0; index < 15; index++) {
                create("Bulk " + index, TaskPriority.LOW);
            }
            statistics.clear();
            assertThat(planning.availableTasks(member.id(), project.id()).tasks()).hasSize(16);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(small);
            assertThat(small).isLessThanOrEqualTo(10);
        } finally {
            statistics.setStatisticsEnabled(previous);
        }
    }

    @Test
    void snapshotWaitsForWriterAndReturnsOneCommittedRevisionAndGraph() throws Exception {
        var predecessor = create("Predecessor", TaskPriority.LOW);
        var successor = create("Successor", TaskPriority.CRITICAL);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                coordination.lockAndAuthorize(owner.id(), project.id(), ProjectCoordination.Permission.WRITE_TASKS);
                edge(successor, predecessor);
                var changed = coordination.markInputChanged(owner.id(), project.id());
                locked.countDown();
                ConcurrentCalls.await(release);
                return changed;
            }));
            ConcurrentCalls.await(locked);
            var reader = executor.submit(() -> planning.availableTasks(member.id(), project.id()));
            release.countDown();
            var committed = writer.get(20, TimeUnit.SECONDS);
            var snapshot = reader.get(20, TimeUnit.SECONDS);
            assertThat(snapshot.inputRevision()).isEqualTo(committed.inputRevision());
            assertThat(snapshot.tasks()).extracting(TaskView::id).containsExactly(predecessor.id());
            assertThat(snapshot.blockingReasons().getFirst().predecessorIds()).containsExactly(predecessor.id());
        } finally {
            release.countDown();
        }
    }

    @Test
    void waitingSnapshotRechecksRevokedMembership() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var version = projects.read(owner.id(), project.id()).version();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                coordination.lockAndAuthorize(owner.id(), project.id(), ProjectCoordination.Permission.MANAGE_PROJECT);
                locked.countDown();
                ConcurrentCalls.await(release);
                return projects.removeMember(owner.id(), project.id(), member.id(), version);
            }));
            ConcurrentCalls.await(locked);
            var reader = executor.submit(ConcurrentCalls.capture(
                    () -> planning.executionOrder(member.id(), project.id()), List.of(ProjectException.class)));
            release.countDown();
            writer.get(20, TimeUnit.SECONDS);
            assertThat(reader.get(20, TimeUnit.SECONDS)).isInstanceOf(ProjectException.class)
                    .extracting("reason").isEqualTo(ProjectException.Reason.PROJECT_NOT_FOUND);
        } finally {
            release.countDown();
        }
    }

    private TaskView create(String name, TaskPriority priority) {
        return tasks.create(owner.id(), project.id(),
                new TaskData(name, "", priority, 15, null, owner.id(), TaskBucket.WORK));
    }

    private void edge(TaskView successor, TaskView predecessor) {
        jdbc.update("insert into task_management.task_dependencies(project_id, task_id, depends_on_task_id) "
                + "values (?, ?, ?)", project.id(), successor.id(), predecessor.id());
    }

    private String route(String endpoint) {
        return "/api/projects/" + project.id() + "/" + endpoint;
    }

    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(identity.login(user.username(), PASSWORD)).accessToken();
    }

    private static RegisterUser command(String name) {
        return new RegisterUser(name, name + "@example.com", PASSWORD, "First", "Last");
    }
}
