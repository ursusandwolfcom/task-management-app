package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.AttachmentOperations;
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
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class AttachmentIntegrationTest {
    private static final String PASSWORD = "attachment-test-passphrase";
    @Autowired private AttachmentOperations attachments;
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
        owner = register("attach_owner");
        member = register("attach_member");
        outsider = register("attach_outsider");
        project = projects.create(owner.id(), new ProjectData("Project", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "UTC"));
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        task = tasks.create(owner.id(), project.id(),
                new TaskData("Task", "", TaskPriority.HIGH, 15, null, null, TaskBucket.WORK));
    }

    @Test
    void uploadListDownloadDeleteRoundTrip() throws Exception {
        byte[] bytes = "hello attachment".getBytes(StandardCharsets.UTF_8);
        var created = attachments.upload(member.id(), task.id(), "note.txt", "text/plain", bytes.length,
                new ByteArrayInputStream(bytes));
        assertThat(created.uploaderId()).isEqualTo(member.id());
        assertThat(created.fileName()).isEqualTo("note.txt");
        assertThat(created.sizeBytes()).isEqualTo(bytes.length);
        assertThat(attachments.list(owner.id(), task.id(), 0, 20).content()).containsExactly(created);

        var downloaded = attachments.download(owner.id(), created.id());
        assertThat(downloaded.body().readAllBytes()).isEqualTo(bytes);
        downloaded.body().close();

        mvc.perform(multipart("/api/attachments").file(new MockMultipartFile("file", "shot.png",
                        "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47})).param("taskId",
                        String.valueOf(task.id())).header("Authorization", bearer(member)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));

        mvc.perform(get("/api/attachments/{id}/content", created.id()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(header().string("Cache-Control", "no-store"));

        attachments.delete(member.id(), created.id());
        assertThat(attachments.list(owner.id(), task.id(), 0, 20).content()).hasSize(1);
    }

    @Test
    void rejectsDisallowedTypeAndProtectsDeletion() throws Exception {
        byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> attachments.upload(member.id(), task.id(), "x.bin", "application/octet-stream",
                bytes.length, new ByteArrayInputStream(bytes)))
                .isInstanceOf(AttachmentException.class).extracting("reason")
                .isEqualTo(AttachmentException.Reason.VALIDATION_ERROR);

        var created = attachments.upload(owner.id(), task.id(), "a.txt", "text/plain", bytes.length,
                new ByteArrayInputStream(bytes));
        assertThatThrownBy(() -> attachments.delete(member.id(), created.id()))
                .isInstanceOf(AttachmentException.class).extracting("reason")
                .isEqualTo(AttachmentException.Reason.FORBIDDEN);
        assertThatThrownBy(() -> attachments.delete(outsider.id(), created.id()))
                .isInstanceOf(AttachmentException.class).extracting("reason")
                .isEqualTo(AttachmentException.Reason.ATTACHMENT_NOT_FOUND);

        mvc.perform(delete("/api/attachments/{id}", created.id()).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
    }

    private UserView register(String name) {
        return identity.register(new RegisterUser(name, name + "@example.com", PASSWORD, "First", "Last"));
    }

    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(identity.login(user.username(), PASSWORD)).accessToken();
    }
}
