package com.example.taskmanagement.collaboration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.collaboration.api.CollaborationException.Reason;
import com.example.taskmanagement.collaboration.api.LabelData;
import com.example.taskmanagement.collaboration.api.LabelOperations;
import com.example.taskmanagement.collaboration.api.LabelView;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.ConcurrentCalls;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
class LabelIntegrationTest {
    private static final String PASSWORD = "label-test-passphrase";
    @Autowired private LabelOperations labels;
    @Autowired private TaskOperations tasks;
    @Autowired private ProjectOperations projects;
    @Autowired private IdentityOperations identity;
    @Autowired private TokenService tokens;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ProjectCoordination coordination;
    private UserView owner;
    private UserView member;
    private UserView outsider;
    private ProjectView project;
    private TaskView task;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        owner = register("label_owner");
        member = register("label_member");
        outsider = register("label_outsider");
        project = projects.create(owner.id(), projectData());
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        task = tasks.create(owner.id(), project.id(),
                new TaskData("Task", "", TaskPriority.HIGH, 15, null, null, TaskBucket.WORK));
    }

    @Test
    void normalizedNameIsUniqueWithinProjectAndColorIsCanonical() {
        var label = create(" Bug ");
        assertThat(label.name()).isEqualTo("Bug");
        assertThat(label.color()).isEqualTo("#AABBCC");
        reason(() -> create(" bug "), Reason.LABEL_ALREADY_EXISTS);
        var other = projects.create(owner.id(), projectData());
        assertThat(labels.create(owner.id(), other.id(), new LabelData("BUG", "#112233")).id())
                .isNotEqualTo(label.id());
        assertThat(labels.list(member.id(), project.id(), 0, 20).content()).containsExactly(label);
    }

    @Test
    void staleReplaceAndDeleteAreRejectedWithoutChangingFacts() {
        var label = create("Bug");
        var changed = labels.replace(member.id(), label.id(), new LabelData("Issue", "#123456"), label.version());
        assertThat(changed.version()).isEqualTo(label.version() + 1);
        reason(() -> labels.replace(owner.id(), label.id(), new LabelData("Lost", "#ABCDEF"), label.version()),
                Reason.VERSION_CONFLICT);
        reason(() -> labels.delete(owner.id(), label.id(), label.version()), Reason.VERSION_CONFLICT);
        assertThat(labels.read(owner.id(), label.id())).isEqualTo(changed);
        var another = create("Other");
        reason(() -> labels.replace(owner.id(), another.id(), new LabelData(" issue ", "#000000"), another.version()),
                Reason.LABEL_ALREADY_EXISTS);
        assertThat(labels.read(owner.id(), another.id())).isEqualTo(another);
    }

    @Test
    void associationsAreIdempotentAndDoNotChangeTaskOrProjectRevisions() {
        var before = projects.read(owner.id(), project.id());
        var label = create("Bug");
        labels.link(member.id(), task.id(), label.id());
        labels.link(member.id(), task.id(), label.id());
        assertThat(labels.taskLabels(owner.id(), task.id(), 0, 20).content()).containsExactly(label);
        labels.unlink(member.id(), task.id(), label.id());
        labels.unlink(member.id(), task.id(), label.id());
        assertThat(labels.taskLabels(owner.id(), task.id(), 0, 20).totalElements()).isZero();
        labels.replace(owner.id(), label.id(), new LabelData("Issue", "#000000"), label.version());
        assertThat(tasks.read(owner.id(), task.id())).isEqualTo(task);
        assertThat(projects.read(owner.id(), project.id())).isEqualTo(before);
    }

    @Test
    void inaccessibleLabelsAreHiddenAndVisibleCrossProjectLinksAreRejected() {
        var label = create("Bug");
        reason(() -> labels.read(outsider.id(), label.id()), Reason.LABEL_NOT_FOUND);
        reason(() -> labels.delete(outsider.id(), label.id(), label.version()), Reason.LABEL_NOT_FOUND);
        var other = projects.create(outsider.id(), projectData());
        var foreign = labels.create(outsider.id(), other.id(), new LabelData("Foreign", "#000000"));
        reason(() -> labels.link(member.id(), task.id(), foreign.id()), Reason.LABEL_NOT_FOUND);
        other = projects.addMember(outsider.id(), other.id(), member.id(), other.version());
        reason(() -> labels.link(member.id(), task.id(), foreign.id()), Reason.CROSS_PROJECT_LABEL);
        assertThatThrownBy(() -> jdbc.update("insert into task_management.task_labels values (?, ?, ?)",
                project.id(), task.id(), foreign.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(labels.taskLabels(owner.id(), task.id(), 0, 20).content()).isEmpty();
    }

    @Test
    void cascadesRemoveOnlyOwnedMetadata() {
        var label = create("Bug");
        labels.link(member.id(), task.id(), label.id());
        tasks.delete(owner.id(), task.id(), task.version());
        assertThat(jdbc.queryForObject("select count(*) from task_management.task_labels", Long.class)).isZero();
        assertThat(labels.read(owner.id(), label.id())).isEqualTo(label);
        var current = projects.read(owner.id(), project.id());
        projects.deleteEmpty(owner.id(), project.id(), current.version());
        assertThat(jdbc.queryForObject("select count(*) from task_management.labels", Long.class)).isZero();
    }

    @Test
    void labelDeletionCascadesAssociationsAndNameCanBeReused() {
        var label = create("Bug");
        labels.link(member.id(), task.id(), label.id());
        labels.delete(member.id(), label.id(), label.version());
        assertThat(labels.taskLabels(owner.id(), task.id(), 0, 20).content()).isEmpty();
        assertThat(create("bug").id()).isNotEqualTo(label.id());
    }

    @Test
    void concurrentDuplicateNamesYieldOneWinnerAndConcurrentLinksStayIdempotent() throws Exception {
        var results = ConcurrentCalls.race(20, List.of(CollaborationException.class),
                () -> create("Bug"), () -> create(" bug "));
        assertThat(results.stream().filter(LabelView.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(CollaborationException.class::isInstance)
                .map(CollaborationException.class::cast).map(CollaborationException::reason))
                .containsExactly(Reason.LABEL_ALREADY_EXISTS);
        var label = labels.list(owner.id(), project.id(), 0, 20).content().getFirst();
        ConcurrentCalls.race(20, List.of(), () -> {
            labels.link(owner.id(), task.id(), label.id());
            return true;
        }, () -> {
            labels.link(member.id(), task.id(), label.id());
            return true;
        });
        assertThat(labels.taskLabels(owner.id(), task.id(), 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void cachedLabelIsRefreshedBeforeExpectedVersionCheck() {
        var label = create("Bug");
        try (var executor = Executors.newSingleThreadExecutor()) {
            reason(() -> new TransactionTemplate(transactionManager).execute(status -> {
                labels.read(owner.id(), label.id());
                CompletableFuture.supplyAsync(() -> labels.replace(member.id(), label.id(),
                        new LabelData("New", "#123456"), label.version()), executor)
                        .orTimeout(20, TimeUnit.SECONDS).join();
                return labels.replace(owner.id(), label.id(), new LabelData("Lost", "#000000"), label.version());
            }), Reason.VERSION_CONFLICT);
        }
        assertThat(labels.read(owner.id(), label.id()).name()).isEqualTo("New");
    }

    @Test
    void revokedMemberCannotWriteAfterWaitingForProjectLock() throws Exception {
        var label = create("Bug");
        var current = projects.read(owner.id(), project.id());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var removal = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                coordination.lockAndAuthorize(owner.id(), project.id(), ProjectCoordination.Permission.MANAGE_PROJECT);
                locked.countDown();
                ConcurrentCalls.await(release);
                return projects.removeMember(owner.id(), project.id(), member.id(), current.version());
            }));
            ConcurrentCalls.await(locked);
            var writer = executor.submit(ConcurrentCalls.capture(() -> {
                labels.link(member.id(), task.id(), label.id());
                return true;
            }, List.of(com.example.taskmanagement.task.api.TaskException.class, CollaborationException.class)));
            release.countDown();
            removal.get(20, TimeUnit.SECONDS);
            assertThat(writer.get(20, TimeUnit.SECONDS)).isInstanceOfAny(
                    com.example.taskmanagement.task.api.TaskException.class, CollaborationException.class);
        } finally {
            release.countDown();
        }
        assertThat(labels.taskLabels(owner.id(), task.id(), 0, 20).content()).isEmpty();
    }

    @Test
    void jdbcLinksAndJpaMetadataRollBackTogether() {
        var label = create("Bug");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            labels.replace(owner.id(), label.id(), new LabelData("Temporary", "#000000"), label.version());
            labels.link(owner.id(), task.id(), label.id());
            status.setRollbackOnly();
        });
        assertThat(labels.read(owner.id(), label.id())).isEqualTo(label);
        assertThat(labels.taskLabels(owner.id(), task.id(), 0, 20).content()).isEmpty();
    }

    @Test
    void httpRequiresExactVersionAndRejectsUnknownFieldsAndInvalidColor() throws Exception {
        String auth = "Bearer " + tokens.issue(identity.login(member.username(), PASSWORD)).accessToken();
        String prefix = "{\"projectId\":" + project.id() + ",\"name\":\"Bug\",\"color\":\"#aabbcc\"";
        mvc.perform(post("/api/labels").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(prefix + "}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.color").value("#AABBCC"));
        mvc.perform(post("/api/labels").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(prefix + ",\"version\":0}")) .andExpect(status().isBadRequest());
        mvc.perform(post("/api/labels").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":" + project.id() + ",\"name\":\"bad\\u0000name\",\"color\":\"#000000\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        var label = create("Other");
        for (String body : new String[] {"{\"name\":\"x\",\"color\":\"#000000\"}",
                "{\"name\":\"x\",\"color\":\"#000000\",\"version\":0.5}",
                "{\"name\":\"x\",\"color\":\"red\",\"version\":0}"}) {
            mvc.perform(put("/api/labels/" + label.id()).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        String route = "/api/tasks/" + task.id() + "/labels/" + label.id();
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(put(route).header("Authorization", auth)).andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(delete(route).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/labels/" + label.id()).param("version", "0").header("Authorization", auth))
                .andExpect(status().isNoContent());
    }

    private LabelView create(String name) {
        return labels.create(member.id(), project.id(), new LabelData(name, "#aabbcc"));
    }

    private UserView register(String name) {
        return identity.register(new RegisterUser(name, name + "@example.com", PASSWORD, "First", "Last"));
    }

    private static ProjectData projectData() {
        return new ProjectData("Project", "", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "UTC");
    }

    private static void reason(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, Reason reason) {
        assertThatThrownBy(action).isInstanceOf(CollaborationException.class).extracting("reason").isEqualTo(reason);
    }
}
