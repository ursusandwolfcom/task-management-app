package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.AttachmentOperations;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.ActivityOperations;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"attachment.storage=dropbox", "dropbox.access-token=offline-test-fixture"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class DropboxDeletionIntegrationTest {
    private static final String PASSWORD = "dropbox-test-passphrase";

    @TestConfiguration
    static class Transport {
        @Bean
        @Primary
        RecordingDropboxFileClient recordingDropboxFileClient() {
            return new RecordingDropboxFileClient();
        }
    }

    @Autowired private AttachmentOperations attachments;
    @Autowired private IdentityOperations identity;
    @Autowired private ProjectOperations projects;
    @Autowired private TaskOperations tasks;
    @Autowired private ActivityOperations activity;
    @Autowired private TokenService tokens;
    @Autowired private RecordingDropboxFileClient client;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    private UserView owner;
    private long taskId;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        client.clear();
        owner = identity.register(new RegisterUser("dropbox_owner", "owner@example.com", PASSWORD, "First", "Last"));
        var project = projects.create(owner.id(), new ProjectData("Dropbox", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "UTC"));
        taskId = tasks.create(owner.id(), project.id(),
                new TaskData("File", "", TaskPriority.HIGH, 15, null, null, TaskBucket.WORK)).id();
    }

    @Test
    void failedDeleteRetainsMetadataAndHistoryThenRetryDeletesTheSameObject() throws Exception {
        byte[] bytes = {1, 2, 3};
        var file = attachments.upload(owner.id(), taskId, "file.txt", "text/plain", bytes.length,
                new ByteArrayInputStream(bytes));
        long historyBefore = activity.list(owner.id(), taskId, 0, 100).totalElements();
        String auth = "Bearer " + tokens.issue(identity.login(owner.username(), PASSWORD)).accessToken();
        client.failDelete = true;
        mvc.perform(delete("/api/attachments/{id}", file.id()).header("Authorization", auth))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("STORAGE_ERROR"));
        assertThat(attachments.list(owner.id(), taskId, 0, 20).content()).containsExactly(file);
        assertThat(activity.list(owner.id(), taskId, 0, 100).totalElements()).isEqualTo(historyBefore);
        try (var body = attachments.download(owner.id(), file.id()).body()) {
            assertThat(body.readAllBytes()).isEqualTo(bytes);
        }
        client.failDelete = false;
        mvc.perform(delete("/api/attachments/{id}", file.id()).header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(attachments.list(owner.id(), taskId, 0, 20).content()).isEmpty();
        assertThat(activity.list(owner.id(), taskId, 0, 100).totalElements()).isEqualTo(historyBefore + 1);
        assertThat(client.size()).isZero();
        assertThat(client.deleteCalls).hasValue(2);
    }

    @Test
    void outsiderCannotTriggerProviderDeletion() {
        var file = attachments.upload(owner.id(), taskId, "file.txt", "text/plain", 1,
                new ByteArrayInputStream(new byte[] {1}));
        var outsider = identity.register(new RegisterUser("dropbox_other", "other@example.com", PASSWORD,
                "First", "Last"));
        assertThatThrownBy(() -> attachments.delete(outsider.id(), file.id()))
                .isInstanceOf(AttachmentException.class).hasMessage("ATTACHMENT_NOT_FOUND");
        assertThat(client.deleteCalls).hasValue(0);
        attachments.delete(owner.id(), file.id());
    }
}
