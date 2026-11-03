package com.example.taskmanagement.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.api.TimeEntryData;
import com.example.taskmanagement.task.api.TimeEntryOperations;
import java.time.LocalDate;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class TimeEntryIntegrationTest {
    private static final String PASSWORD = "time-entry-test-passphrase";
    @Autowired private TimeEntryOperations entries;
    @Autowired private TaskOperations tasks;
    @Autowired private ProjectOperations projects;
    @Autowired private IdentityOperations identity;
    @Autowired private TokenService tokens;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private UserView owner;
    private UserView member;
    private UserView outsider;
    private ProjectView project;
    private TaskView task;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        owner = register("time_owner");
        member = register("time_member");
        outsider = register("time_outsider");
        project = projects.create(owner.id(), new ProjectData("Project", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "UTC"));
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        task = tasks.create(owner.id(), project.id(),
                new TaskData("Task", "", TaskPriority.HIGH, 60, null, null, TaskBucket.WORK));
    }

    @Test
    void logsActualMinutesWithoutChangingPlanningRevisions() {
        var before = projects.read(owner.id(), project.id());
        var entry = entries.create(member.id(),
                new TimeEntryData(task.id(), LocalDate.of(2026, 11, 3), 90, "  review  "));
        assertThat(entry.userId()).isEqualTo(member.id());
        assertThat(entry.minutes()).isEqualTo(90);
        assertThat(entry.note()).isEqualTo("review");
        var page = entries.list(owner.id(), task.id(), 0, 20);
        assertThat(page.content()).containsExactly(entry);
        assertThat(page.totalMinutes()).isEqualTo(90);
        assertThat(tasks.read(owner.id(), task.id())).isEqualTo(task);
        assertThat(projects.read(owner.id(), project.id())).isEqualTo(before);
    }

    @Test
    void deletionRequiresAuthorOwnerOrAdminAndCascadeOnTaskDelete() {
        var entry = entries.create(owner.id(), new TimeEntryData(task.id(), LocalDate.of(2026, 11, 3), 30, null));
        assertThatThrownBy(() -> entries.delete(member.id(), entry.id()))
                .isInstanceOf(TaskException.class).extracting("reason")
                .isEqualTo(TaskException.Reason.FORBIDDEN);
        assertThatThrownBy(() -> entries.delete(outsider.id(), entry.id()))
                .isInstanceOf(TaskException.class).extracting("reason")
                .isEqualTo(TaskException.Reason.TASK_NOT_FOUND);
        var own = entries.create(member.id(), new TimeEntryData(task.id(), LocalDate.of(2026, 11, 4), 15, null));
        entries.delete(member.id(), own.id());
        entries.delete(owner.id(), entry.id());
        assertThat(entries.list(owner.id(), task.id(), 0, 20).totalMinutes()).isZero();
        var leftover = entries.create(owner.id(), new TimeEntryData(task.id(), LocalDate.of(2026, 11, 5), 10, null));
        assertThat(leftover.id()).isPositive();
        tasks.delete(owner.id(), task.id(), task.version());
        assertThat(jdbc.queryForObject("select count(*) from task_management.task_time_entries", Long.class))
                .isZero();
    }

    @Test
    void httpRejectsOutOfRangeMinutesAndUnknownFields() throws Exception {
        String auth = "Bearer " + tokens.issue(identity.login(member.username(), PASSWORD)).accessToken();
        mvc.perform(post("/api/time-entries").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":" + task.id() + ",\"workDate\":\"2026-11-03\",\"minutes\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/time-entries").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":" + task.id() + ",\"workDate\":\"2026-11-03\",\"minutes\":30,\"userId\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private UserView register(String name) {
        return identity.register(new RegisterUser(name, name + "@example.com", PASSWORD, "First", "Last"));
    }
}
