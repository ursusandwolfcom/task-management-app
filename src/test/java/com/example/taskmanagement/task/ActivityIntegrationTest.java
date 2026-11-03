package com.example.taskmanagement.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.task.api.ActivityEvents;
import com.example.taskmanagement.task.api.ActivityOperations;
import com.example.taskmanagement.collaboration.api.CommentData;
import com.example.taskmanagement.collaboration.api.CommentOperations;
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
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class ActivityIntegrationTest {
    private static final String PASSWORD = "activity-test-passphrase";
    @Autowired private ActivityOperations activities;
    @Autowired private TaskOperations tasks;
    @Autowired private CommentOperations comments;
    @Autowired private ProjectOperations projects;
    @Autowired private IdentityOperations identity;
    @Autowired private TokenService tokens;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private UserView owner;
    private UserView outsider;
    private ProjectView project;
    private TaskView task;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        owner = register("activity_owner");
        outsider = register("activity_outsider");
        project = projects.create(owner.id(), new ProjectData("Project", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "UTC"));
        task = tasks.create(owner.id(), project.id(),
                new TaskData("Task", "", TaskPriority.HIGH, 30, null, owner.id(), TaskBucket.WORK));
    }

    @Test
    void createCommentAndLifecycleAppendNewestFirstAndHideFromOutsider() throws Exception {
        comments.create(owner.id(), new CommentData(task.id(), "Please review"));
        task = tasks.execute(owner.id(), task.id(), Command.START, task.version());
        var page = activities.list(owner.id(), task.id(), 0, 20);
        assertThat(page.content()).extracting(item -> item.actionType())
                .containsExactly(ActivityEvents.TASK_STARTED, ActivityEvents.COMMENT_CREATED,
                        ActivityEvents.TASK_CREATED);
        assertThatThrownBy(() -> activities.list(outsider.id(), task.id(), 0, 20))
                .isInstanceOf(TaskException.class).extracting("reason")
                .isEqualTo(TaskException.Reason.TASK_NOT_FOUND);

        String auth = "Bearer " + tokens.issue(identity.login(owner.username(), PASSWORD)).accessToken();
        mvc.perform(get("/api/tasks/{id}/activity", task.id()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actionType").value(ActivityEvents.TASK_STARTED))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void taskDeletionCascadesActivityRows() {
        assertThat(jdbc.queryForObject(
                "select count(*) from task_management.task_activities where task_id = ?", Long.class, task.id()))
                .isEqualTo(1L);
        tasks.delete(owner.id(), task.id(), task.version());
        assertThat(jdbc.queryForObject(
                "select count(*) from task_management.task_activities where task_id = ?", Long.class, task.id()))
                .isZero();
    }

    private UserView register(String login) {
        return identity.register(new RegisterUser(login, login + "@example.com", PASSWORD, "Act", "User"));
    }
}
