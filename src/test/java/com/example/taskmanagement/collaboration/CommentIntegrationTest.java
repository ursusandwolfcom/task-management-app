package com.example.taskmanagement.collaboration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.collaboration.api.CollaborationException;
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
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
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
class CommentIntegrationTest {
    private static final String PASSWORD = "comment-test-passphrase";
    @Autowired private CommentOperations comments;
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
        owner = register("comment_owner");
        member = register("comment_member");
        outsider = register("comment_outsider");
        project = projects.create(owner.id(), new ProjectData("Project", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "UTC"));
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        task = tasks.create(owner.id(), project.id(), new TaskData("Task", "", TaskPriority.HIGH, 15, null, null));
    }

    @Test
    void plainTextAndServerFactsRoundTripWithoutChangingTaskOrProject() {
        var before = projects.read(owner.id(), project.id());
        var comment = comments.create(member.id(), new CommentData(task.id(), "  <script>text</script>  "));
        assertThat(comment.authorId()).isEqualTo(member.id());
        assertThat(comment.text()).isEqualTo("<script>text</script>");
        assertThat(comment.createdAt().getNano() % 1000).isZero();
        assertThat(comments.list(owner.id(), task.id(), 0, 20).content()).containsExactly(comment);
        assertThat(tasks.read(owner.id(), task.id())).isEqualTo(task);
        assertThat(projects.read(owner.id(), project.id())).isEqualTo(before);
    }

    @Test
    void deletionRequiresCurrentAccessAndAuthorOwnerOrAdmin() {
        var comment = comments.create(owner.id(), new CommentData(task.id(), "Owner comment"));
        assertThatThrownBy(() -> comments.delete(member.id(), comment.id()))
                .isInstanceOf(CollaborationException.class).extracting("reason")
                .isEqualTo(CollaborationException.Reason.FORBIDDEN);
        assertThatThrownBy(() -> comments.delete(outsider.id(), comment.id()))
                .isInstanceOf(CollaborationException.class).extracting("reason")
                .isEqualTo(CollaborationException.Reason.COMMENT_NOT_FOUND);
        var own = comments.create(member.id(), new CommentData(task.id(), "Member comment"));
        project = projects.read(owner.id(), project.id());
        projects.removeMember(owner.id(), project.id(), member.id(), project.version());
        assertThatThrownBy(() -> comments.delete(member.id(), own.id())).isInstanceOf(CollaborationException.class);
        comments.delete(owner.id(), own.id());
        comments.delete(owner.id(), comment.id());
        assertThat(comments.list(owner.id(), task.id(), 0, 20).content()).isEmpty();
    }

    @Test
    void parentDeletionCascadesCommentsAndPaginationIsBounded() {
        var first = comments.create(member.id(), new CommentData(task.id(), "First"));
        var second = comments.create(owner.id(), new CommentData(task.id(), "Second"));
        assertThat(comments.list(owner.id(), task.id(), 0, 1).content()).containsExactly(second);
        assertThat(comments.list(owner.id(), task.id(), 1, 1).content()).containsExactly(first);
        assertThatThrownBy(() -> comments.list(owner.id(), task.id(), 0, 101))
                .isInstanceOf(CollaborationException.class);
        tasks.delete(owner.id(), task.id(), task.version());
        assertThat(jdbc.queryForObject("select count(*) from task_management.comments", Long.class)).isZero();
    }

    @Test
    void httpRejectsSpoofingFractionsAndBlankText() throws Exception {
        String auth = "Bearer " + tokens.issue(identity.login(member.username(), PASSWORD)).accessToken();
        for (String body : new String[] {"{\"taskId\":" + task.id() + ",\"text\":\"x\",\"authorId\":1}",
                "{\"taskId\":1.5,\"text\":\"x\"}", "{\"taskId\":" + task.id() + ",\"text\":\"   \"}"}) {
            mvc.perform(post("/api/comments").header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/comments").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":" + task.id() + ",\"text\":\"Hello\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorId").value(member.id()));
    }

    private UserView register(String name) {
        return identity.register(new RegisterUser(name, name + "@example.com", PASSWORD, "First", "Last"));
    }
}
