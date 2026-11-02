package com.example.taskmanagement.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.ProfileChange;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.security.web.BrowserSessionCookies;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class StoredTextIntegrationTest {
    private static final String PASSWORD = "stored-text-test-passphrase";
    private static final String INVALID = "before\0after";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private IdentityOperations identity;
    @Autowired private ProjectOperations projects;
    @Autowired private TaskOperations tasks;
    @Autowired private TokenService tokens;
    @Autowired private JdbcTemplate jdbc;
    private UserView owner;
    private ProjectView project;
    private TaskView task;
    private String auth;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        owner = identity.register(new RegisterUser("text_owner", "text-owner@example.com", PASSWORD, "First", "Last"));
        project = projects.create(owner.id(), new ProjectData("Project", "Description",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "UTC"));
        task = tasks.create(owner.id(), project.id(),
                new TaskData("Task", "Description", TaskPriority.HIGH, 15, null, null, TaskBucket.WORK));
        project = projects.read(owner.id(), project.id());
        auth = "Bearer " + tokens.issue(identity.login(owner.username(), PASSWORD)).accessToken();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "description"})
    void projectCreationRejectsNulWithoutWriting(String field) throws Exception {
        var body = projectBody();
        body.put(field, INVALID);
        reject(HttpMethod.POST, "/api/projects", body);
        assertThat(projects.list(owner.id(), 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void directModuleCommandsRejectNulBeforePersistence() {
        assertThatThrownBy(() -> projects.create(owner.id(), new ProjectData(INVALID, "Description",
                project.startDate(), project.endDate(), project.timezone())))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> tasks.create(owner.id(), project.id(),
                new TaskData(INVALID, "Description", TaskPriority.HIGH, 15, null, null, TaskBucket.INBOX)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> identity.register(new RegisterUser("text_other", "text-other@example.com",
                PASSWORD, INVALID, "Last"))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> identity.updateProfile(owner.id(), new ProfileChange(owner.username(), owner.email(),
                "First", INVALID, owner.version()))).isInstanceOf(ConstraintViolationException.class);
        assertThat(projects.read(owner.id(), project.id())).isEqualTo(project);
        assertThat(tasks.read(owner.id(), task.id())).isEqualTo(task);
        assertThat(identity.profile(owner.id())).isEqualTo(owner);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auth", "session"})
    void loginsRejectNulBeforeQueryingUsers(String adapter) throws Exception {
        var login = request(HttpMethod.POST, "/api/" + adapter + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier", INVALID, "password", PASSWORD)));
        if (adapter.equals("session")) {
            var csrf = mvc.perform(request(HttpMethod.GET, "/api/session/csrf"))
                    .andReturn().getResponse();
            var token = json.readTree(csrf.getContentAsString());
            login.cookie(csrf.getCookie(BrowserSessionCookies.CSRF))
                    .header(token.path("headerName").asText(), token.path("token").asText());
        }
        mvc.perform(login).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void moduleLoginRejectsNulBeforeQueryingUsers() {
        assertThatThrownBy(() -> identity.login(INVALID, PASSWORD)).isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({"name,PUT", "name,PATCH", "description,PUT", "description,PATCH"})
    void projectUpdatesRejectNulWithoutChangingMetadataOrRevision(String field, String method) throws Exception {
        var body = method.equals("PUT") ? projectBody() : new LinkedHashMap<String, Object>();
        body.put(field, INVALID);
        body.put("version", project.version());
        reject(HttpMethod.valueOf(method), "/api/projects/" + project.id(), body);
        assertThat(projects.read(owner.id(), project.id())).isEqualTo(project);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "description"})
    void taskCreationRejectsNulWithoutWriting(String field) throws Exception {
        var body = taskBody();
        body.put("projectId", project.id());
        body.put(field, INVALID);
        reject(HttpMethod.POST, "/api/tasks", body);
        assertThat(tasks.list(owner.id(), project.id(),
                com.example.taskmanagement.task.api.TaskFilter.empty(), 0, 20).totalElements()).isEqualTo(1);
        assertThat(projects.read(owner.id(), project.id())).isEqualTo(project);
    }

    @ParameterizedTest
    @CsvSource({"name,PUT", "name,PATCH", "description,PUT", "description,PATCH"})
    void taskUpdatesRejectNulWithoutChangingMetadataOrRevision(String field, String method) throws Exception {
        var body = method.equals("PUT") ? taskBody() : new LinkedHashMap<String, Object>();
        body.put(field, INVALID);
        body.put("version", task.version());
        reject(HttpMethod.valueOf(method), "/api/tasks/" + task.id(), body);
        assertThat(tasks.read(owner.id(), task.id())).isEqualTo(task);
        assertThat(projects.read(owner.id(), project.id())).isEqualTo(project);
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName"})
    void registrationRejectsNulWithoutCreatingUser(String field) throws Exception {
        var body = new LinkedHashMap<String, Object>(Map.of("username", "new_text_user",
                "email", "new-text@example.com", "password", PASSWORD, "firstName", "First", "lastName", "Last"));
        body.put(field, INVALID);
        reject(HttpMethod.POST, "/api/auth/register", body);
        assertThat(jdbc.queryForObject("select count(*) from task_management.users", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"firstName,PUT", "firstName,PATCH", "lastName,PUT", "lastName,PATCH"})
    void profileUpdatesRejectNulWithoutChangingUser(String field, String method) throws Exception {
        var body = method.equals("PUT") ? new LinkedHashMap<String, Object>(Map.of("username", owner.username(),
                "email", owner.email(), "firstName", owner.firstName(), "lastName", owner.lastName()))
                : new LinkedHashMap<String, Object>();
        body.put(field, INVALID);
        body.put("version", owner.version());
        reject(HttpMethod.valueOf(method), "/api/users/me", body);
        assertThat(identity.profile(owner.id())).isEqualTo(owner);
    }

    private void reject(HttpMethod method, String route, Map<String, Object> body) throws Exception {
        mvc.perform(request(method, route).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static LinkedHashMap<String, Object> projectBody() {
        return new LinkedHashMap<>(Map.of("name", "Project", "description", "Description",
                "startDate", "2026-01-01", "endDate", "2027-01-01", "timezone", "UTC"));
    }

    private static LinkedHashMap<String, Object> taskBody() {
        return new LinkedHashMap<>(Map.of("name", "Task", "description", "Description", "priority", "HIGH",
                "estimatedMinutes", 15));
    }
}
