package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.attachment.api.AttachmentOperations;
import com.example.taskmanagement.attachment.api.MegaClient;
import com.example.taskmanagement.attachment.api.MegaException;
import com.example.taskmanagement.attachment.api.MegaOperations;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskException;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "mega.session-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class MegaIntegrationTest {
    private static final String PASSWORD = "mega-test-passphrase";
    @TestConfiguration
    static class Transport {
        @Bean
        @Primary
        RecordingMegaClient recordingMegaClient() {
            return new RecordingMegaClient();
        }
    }

    @Autowired private MegaOperations mega;
    @Autowired private AttachmentOperations attachments;
    @Autowired private IdentityOperations identity;
    @Autowired private ProjectOperations projects;
    @Autowired private TaskOperations tasks;
    @Autowired private RecordingMegaClient client;
    @Autowired private TokenService tokens;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private UserView owner;
    private UserView member;
    private UserView outsider;
    private long taskId;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        client.files.clear();
        client.failUpload = false;
        owner = identity.register(new RegisterUser("mega_owner", "owner@example.com", PASSWORD, "First", "Last"));
        member = identity.register(new RegisterUser("mega_member", "member@example.com", PASSWORD, "First", "Last"));
        outsider = identity.register(new RegisterUser("mega_other", "other@example.com", PASSWORD, "First", "Last"));
        var project = projects.create(owner.id(), new ProjectData("MEGA", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "UTC"));
        projects.addMember(owner.id(), project.id(), member.id(), project.version());
        taskId = tasks.create(owner.id(), project.id(),
                new TaskData("File", "", TaskPriority.HIGH, 15, null, null, TaskBucket.WORK)).id();
    }

    @Test
    void reconnectUsesOriginalAccountAndPreservesLocalAndRemoteFiles() throws Exception {
        byte[] bytes = new byte[] {1, 2, 3};
        var local = attachments.upload(owner.id(), taskId, "local.txt", "text/plain", bytes.length,
                new ByteArrayInputStream(bytes));
        mega.link(owner.id(), "owner@mega.test", "mega-password", null);
        mega.link(member.id(), "other@mega.test", "mega-password", null);
        var created = attachments.upload(owner.id(), taskId, "mega.txt", "text/plain", bytes.length,
                new ByteArrayInputStream(bytes));
        assertThat(jdbc.queryForObject("select external_id from task_management.attachments where id=?",
                String.class, created.id())).startsWith("mega:accountA001:");
        try (var body = attachments.download(member.id(), created.id()).body()) {
            assertThat(body.readAllBytes()).isEqualTo(bytes);
        }
        assertThat(client.downloadedAccount).isEqualTo("accountA001");
        assertThatThrownBy(() -> attachments.download(outsider.id(), created.id()))
                .isInstanceOf(TaskException.class).hasMessage("TASK_NOT_FOUND");
        mega.unlink(owner.id());
        assertThat(client.files).hasSize(1);
        assertThat(attachments.list(owner.id(), taskId, 0, 20).content()).hasSize(2);
        assertThatThrownBy(() -> attachments.download(member.id(), created.id()))
                .isInstanceOf(MegaException.class).hasMessage("MEGA_ACCOUNT_UNAVAILABLE");
        mega.link(owner.id(), "other@mega.test", "mega-password", null);
        assertThatThrownBy(() -> attachments.download(owner.id(), created.id()))
                .isInstanceOf(MegaException.class).hasMessage("MEGA_ACCOUNT_UNAVAILABLE");
        mega.link(owner.id(), "owner@mega.test", "mega-password", null);
        try (var body = attachments.download(member.id(), created.id()).body()) {
            assertThat(body.readAllBytes()).isEqualTo(bytes);
        }
        try (var body = attachments.download(member.id(), local.id()).body()) {
            assertThat(body.readAllBytes()).isEqualTo(bytes);
        }
        attachments.delete(owner.id(), created.id());
        assertThat(client.files).isEmpty();
    }

    @Test
    void encryptsStoredSessionAndNeverPersistsPasswordAndPreservesLinkOnBadLogin() {
        mega.link(owner.id(), "owner@mega.test", "mega-password", null);
        String encrypted = jdbc.queryForObject(
                "select encrypted_session from task_management.user_mega_links where user_id=?",
                String.class, owner.id());
        assertThat(encrypted).doesNotContain("sensitive-session", "mega-password");
        assertThatThrownBy(() -> mega.link(owner.id(), "other@mega.test", "wrong-password", null))
                .isInstanceOf(MegaException.class).hasMessage("MEGA_AUTHENTICATION_FAILED");
        assertThat(mega.status(owner.id()).accountId()).isEqualTo("accountA001");
        assertThat(new MegaClient.Session("account", "secret", "folder").toString()).doesNotContain("secret");
    }

    @Test
    void failedUploadDoesNotCreateMetadataOrClaimSuccess() {
        mega.link(owner.id(), "owner@mega.test", "mega-password", null);
        client.failUpload = true;
        assertThatThrownBy(() -> attachments.upload(owner.id(), taskId, "fail.txt", "text/plain", 3,
                new ByteArrayInputStream(new byte[] {1, 2, 3})))
                .isInstanceOf(MegaException.class).hasMessage("MEGA_STORAGE_ERROR");
        assertThat(attachments.list(owner.id(), taskId, 0, 20).content()).isEmpty();
    }

    @Test
    void httpRequiresAuthenticationValidatesMfaAndDoesNotExposeSession() throws Exception {
        mvc.perform(get("/api/mega")).andExpect(status().isUnauthorized());
        String auth = "Bearer " + tokens.issue(identity.login(owner.username(), PASSWORD)).accessToken();
        mvc.perform(post("/api/mega/link").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mfa@mega.test\",\"password\":\"mega-password\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEGA_MFA_REQUIRED"));
        mvc.perform(post("/api/mega/link").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mfa@mega.test\",\"password\":\"mega-password\",\"mfaCode\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.linked").value(true)).andExpect(jsonPath("$.session").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/api/mega").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value("accountA001"));
    }
}
