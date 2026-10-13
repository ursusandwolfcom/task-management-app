package com.example.taskmanagement.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.taskmanagement.support.ConcurrentCalls.await;
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
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectCoordination.Permission;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.ConcurrentCalls;
import com.example.taskmanagement.support.DatabaseFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class ProjectIntegrationTest {
    private static final String PASSWORD = "project-test-passphrase";
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private IdentityOperations identity;
    @Autowired
    private TokenService tokens;
    @Autowired
    private ProjectOperations projects;
    @Autowired
    private ProjectCoordination coordination;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UserView owner;
    private UserView member;
    private UserView outsider;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        owner = register("owner");
        member = register("member");
        outsider = register("outsider");
    }

    @Test
    void createsOwnerMembershipAndIgnoresClientOwnershipStatusAndRevision() throws Exception {
        var result = mvc.perform(post("/api/projects").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "name", " First project ", "description", " Description ", "startDate", "2026-10-13",
                        "endDate", "2026-10-14", "ownerId", outsider.id(), "status", "COMPLETED", "version", 50))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerId").value(owner.id()))
                .andExpect(jsonPath("$.status").value("INITIATED")).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.name").value("First project")).andExpect(jsonPath("$.timezone").value("UTC"))
                .andReturn().getResponse();
        var body = json.readTree(result.getContentAsString());
        long id = body.path("id").asLong();
        assertThat(result.getHeader("Location")).isEqualTo("/api/projects/" + id);
        assertThat(result.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(memberCount(id)).isEqualTo(1);
        mvc.perform(get("/api/projects/" + id + "/members").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].userId").value(owner.id()))
                .andExpect(jsonPath("$.content[0].owner").value(true));
    }

    @Test
    void creationRollbackLeavesNeitherProjectNorOwnerMembership() {
        assertThatThrownBy(() -> transaction().execute(status -> {
            projects.create(owner.id(), data("Rolled back"));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback");
        assertThat(projects.list(owner.id(), 0, 20).totalElements()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from task_management.project_members", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from task_management.users", Integer.class)).isEqualTo(3);
    }

    @Test
    void outsiderAndMissingProjectHaveIdenticalNotFoundErrors() throws Exception {
        var project = create();
        String token = bearer(outsider);
        for (long id : new long[] {project.id(), Long.MAX_VALUE}) {
            mvc.perform(get("/api/projects/" + id).header("Authorization", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("The project operation could not be completed."));
            mvc.perform(get("/api/projects/" + id + "/members").header("Authorization", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
            mvc.perform(patch("/api/projects/" + id).header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hidden\",\"version\":0}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        }
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyAccessibleProjectsAndAdminCanManageAll() throws Exception {
        var first = create();
        var second = projects.create(owner.id(), data("Second"));
        projects.create(outsider.id(), data("Hidden"));
        projects.addMember(owner.id(), first.id(), member.id(), first.version());
        assertThat(projects.list(owner.id(), 0, 1).content()).extracting(ProjectView::id).containsExactly(second.id());
        assertThat(projects.list(owner.id(), 1, 1).content()).extracting(ProjectView::id).containsExactly(first.id());
        assertThat(projects.list(member.id(), 0, 20).content()).extracting(ProjectView::id).containsExactly(first.id());
        var admin = identity.bootstrapAdmin(command("admin"));
        assertThat(projects.list(admin.id(), 0, 20).totalElements()).isEqualTo(3);
        var changed = projects.replace(admin.id(), second.id(), data("Admin edit"), second.version());
        assertThat(changed.name()).isEqualTo("Admin edit");
        mvc.perform(get("/api/projects").header("Authorization", bearer(member)).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void memberReadsAndCoordinatesTasksButCannotManageProjectOrMembers() throws Exception {
        var project = create();
        var withMember = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        String token = bearer(member);
        mvc.perform(get("/api/projects/" + project.id()).header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(patch("/api/projects/" + project.id()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\",\"version\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/projects/" + project.id() + "/members/" + outsider.id()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/" + project.id()).header("Authorization", token).param("version", "1"))
                .andExpect(status().isForbidden());
        var snapshot = transaction().execute(status ->
                coordination.lockAndAuthorize(member.id(), project.id(), Permission.WRITE_TASKS));
        assertThat(snapshot.id()).isEqualTo(withMember.id());
    }

    @Test
    void patchPreservesOmittedFieldsAndVersionGuardsAllMutations() throws Exception {
        var project = create();
        String token = bearer(owner);
        var changed = json.readTree(mvc.perform(patch("/api/projects/" + project.id()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"status\":\"COMPLETED\",\"ownerId\":999,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.status").value("INITIATED")).andReturn().getResponse().getContentAsString());
        assertThat(changed.path("description").asText()).isEqualTo(project.description());
        assertThat(changed.path("calendarRevision").asLong()).isZero();
        assertThat(changed.path("inputRevision").asLong()).isEqualTo(1);
        mvc.perform(put("/api/projects/" + project.id() + "/members/" + member.id())
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(delete("/api/projects/" + project.id()).header("Authorization", token).param("version", "0"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/projects/" + project.id()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "name", "Replacement", "startDate", "2026-10-14", "endDate", "2026-10-15",
                        "timezone", "Europe/Kyiv", "version", changed.path("version").asLong()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.calendarRevision").value(1));
    }

    @Test
    void invalidInputsAndDatabaseDatesNeverChangeProject() throws Exception {
        var project = create();
        String token = bearer(owner);
        for (String invalid : List.of("{\"name\":\" \",\"version\":0}",
                "{\"endDate\":\"2026-10-12\",\"version\":0}",
                "{\"timezone\":\"Mars/City\",\"version\":0}", "{\"timezone\":\"+03:00\",\"version\":0}",
                "{\"name\":\"" + "x".repeat(201) + "\",\"version\":0}", "{\"name\":\"Missing version\"}")) {
            mvc.perform(patch("/api/projects/" + project.id()).header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/projects").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Missing dates\"}"))
                .andExpect(status().isBadRequest());
        assertThat(projects.read(owner.id(), project.id()).version()).isZero();
        assertThatThrownBy(() -> jdbc.update("update task_management.projects set end_date = ? where id = ?",
                java.sql.Date.valueOf("2026-10-12"), project.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(projects.read(owner.id(), project.id()).endDate()).isEqualTo(project.endDate());
    }

    @Test
    void duplicateAndMissingMembersDoNotChangeProjectAndOwnerCannotBeRemoved() throws Exception {
        var project = create();
        var updated = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        assertThatThrownBy(() -> projects.addMember(owner.id(), project.id(), member.id(), updated.version()))
                .isInstanceOf(ProjectException.class).hasMessage("MEMBER_ALREADY_EXISTS");
        assertThatThrownBy(() -> projects.removeMember(owner.id(), project.id(), owner.id(), updated.version()))
                .isInstanceOf(ProjectException.class).hasMessage("OWNER_REMOVAL_FORBIDDEN");
        assertThatThrownBy(() -> projects.removeMember(owner.id(), project.id(), outsider.id(), updated.version()))
                .isInstanceOf(ProjectException.class).hasMessage("MEMBER_NOT_FOUND");
        assertThat(memberCount(project.id())).isEqualTo(2);
        var removed = json.readTree(mvc.perform(delete("/api/projects/" + project.id() + "/members/" + member.id())
                .header("Authorization", bearer(owner)).param("version", Long.toString(updated.version())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(removed.path("version").asLong()).isGreaterThan(updated.version());
        assertThatThrownBy(() -> projects.read(member.id(), project.id()))
                .isInstanceOf(ProjectException.class).hasMessage("PROJECT_NOT_FOUND");
    }

    @Test
    void ownershipMovesOnlyToExistingMemberAndOldOwnerRemainsMember() throws Exception {
        var project = create();
        var updated = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        assertThatThrownBy(() -> projects.transferOwner(owner.id(), project.id(), outsider.id(), updated.version()))
                .isInstanceOf(ProjectException.class).hasMessage("MEMBER_NOT_FOUND");
        mvc.perform(put("/api/projects/" + project.id() + "/owner").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "userId", member.id(), "version", updated.version()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(member.id()));
        var transferred = projects.read(owner.id(), project.id());
        assertThat(memberCount(project.id())).isEqualTo(2);
        assertThatThrownBy(() -> projects.replace(owner.id(), project.id(), data("Denied"), transferred.version()))
                .isInstanceOf(ProjectException.class).hasMessage("FORBIDDEN");
        assertThatThrownBy(() -> projects.removeMember(member.id(), project.id(), member.id(), transferred.version()))
                .isInstanceOf(ProjectException.class).hasMessage("OWNER_REMOVAL_FORBIDDEN");
        projects.replace(member.id(), project.id(), data("New owner"), transferred.version());
    }

    @Test
    void deletionRemovesOnlyProjectAndMembershipAndPreservesUsers() throws Exception {
        var project = create();
        var updated = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        mvc.perform(delete("/api/projects/" + project.id()).header("Authorization", bearer(owner))
                .param("version", Long.toString(updated.version()))).andExpect(status().isNoContent());
        assertThat(memberCount(project.id())).isZero();
        assertThat(projects.list(owner.id(), 0, 20).totalElements()).isZero();
        assertThat(identity.profile(owner.id()).id()).isEqualTo(owner.id());
        assertThat(identity.profile(member.id()).id()).isEqualTo(member.id());
    }

    @Test
    void concurrentChangesWithSameVersionHaveExactlyOneWinner() throws Exception {
        var project = create();
        var barrier = new CyclicBarrier(2);
        var results = race(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            return projects.replace(owner.id(), project.id(), data("One"), project.version());
        }, () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return projects.replace(owner.id(), project.id(), data("Two"), project.version());
        });
        assertThat(results.stream().filter(ProjectView.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(ProjectException.class::isInstance)
                .map(ProjectException.class::cast).map(Throwable::getMessage)).containsExactly("VERSION_CONFLICT");
        assertThat(projects.read(owner.id(), project.id()).version()).isEqualTo(1);
    }

    @Test
    void concurrentTransferAndRemovalAlwaysPreserveOwnerMembership() throws Exception {
        var original = create();
        var project = projects.addMember(owner.id(), original.id(), member.id(), original.version());
        var barrier = new CyclicBarrier(2);
        var results = race(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            return projects.transferOwner(owner.id(), project.id(), member.id(), project.version());
        }, () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return projects.removeMember(owner.id(), project.id(), member.id(), project.version());
        });
        assertThat(results.stream().filter(ProjectView.class::isInstance).count()).isEqualTo(1);
        var current = projects.read(owner.id(), project.id());
        assertThat(jdbc.queryForObject("select count(*) from task_management.project_members "
                + "where project_id = ? and user_id = ?", Integer.class, current.id(), current.ownerId())).isEqualTo(1);
    }

    @Test
    void coordinationRechecksRemovedMembershipAfterWaitingForProjectLock() throws Exception {
        var original = create();
        var project = projects.addMember(owner.id(), original.id(), member.id(), original.version());
        var observed = new CountDownLatch(1);
        var locked = new CountDownLatch(1);
        var attempting = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var reader = executor.submit(() -> capture(() -> transaction().execute(status -> {
                projects.read(member.id(), project.id());
                observed.countDown();
                await(locked);
                attempting.countDown();
                return coordination.lockAndAuthorize(member.id(), project.id(), Permission.WRITE_TASKS);
            })).call());
            var writer = executor.submit(() -> transaction().execute(status -> {
                await(observed);
                coordination.lockAndAuthorize(owner.id(), project.id(), Permission.MANAGE_PROJECT);
                locked.countDown();
                await(attempting);
                return projects.removeMember(owner.id(), project.id(), member.id(), project.version());
            }));
            assertThat(writer.get(20, TimeUnit.SECONDS).id()).isEqualTo(project.id());
            assertThat(reader.get(20, TimeUnit.SECONDS)).isInstanceOf(ProjectException.class);
        }
    }

    @Test
    void cachedAdminRoleCannotBypassDemotionWithinExistingTransaction() throws Exception {
        var project = create();
        var admin = identity.bootstrapAdmin(command("admin"));
        var elevated = identity.changeRole(admin.id(), outsider.id(), UserRole.ADMIN, outsider.version());
        var observed = new CountDownLatch(1);
        var demoted = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> {
                await(observed);
                var result = identity.changeRole(admin.id(), outsider.id(), UserRole.USER, elevated.version());
                demoted.countDown();
                return result;
            });
            assertThatThrownBy(() -> transaction().execute(status -> {
                assertThat(identity.profile(outsider.id()).role()).isEqualTo(UserRole.ADMIN);
                observed.countDown();
                await(demoted);
                return coordination.lockAndAuthorize(outsider.id(), project.id(), Permission.MANAGE_PROJECT);
            })).isInstanceOf(ProjectException.class).hasMessage("PROJECT_NOT_FOUND");
            assertThat(writer.get(20, TimeUnit.SECONDS).role()).isEqualTo(UserRole.USER);
        }
    }

    @Test
    void coordinationRequiresTransactionAndRevisionChangeIsAtomic() {
        var project = create();
        assertThatThrownBy(() -> coordination.lockAndAuthorize(owner.id(), project.id(), Permission.READ))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> transaction().execute(status -> {
            coordination.markInputChanged(owner.id(), project.id());
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback");
        assertThat(projects.read(owner.id(), project.id()).inputRevision()).isZero();
    }

    private UserView register(String name) {
        return identity.register(command(name));
    }

    private static RegisterUser command(String name) {
        return new RegisterUser(name, name + "@example.com", PASSWORD, "First", "Last");
    }

    private ProjectView create() {
        return projects.create(owner.id(), data("Project"));
    }

    private static ProjectData data(String name) {
        return new ProjectData(name, "Description", LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14), "UTC");
    }

    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(identity.login(user.username(), PASSWORD)).accessToken();
    }

    private int memberCount(long projectId) {
        return jdbc.queryForObject("select count(*) from task_management.project_members where project_id = ?",
                Integer.class, projectId);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        return ConcurrentCalls.race(20, List.of(ProjectException.class), first, second);
    }

    private static Callable<Object> capture(Callable<Object> action) {
        return ConcurrentCalls.capture(action, List.of(ProjectException.class));
    }
}
