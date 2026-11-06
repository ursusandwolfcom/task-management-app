package com.example.taskmanagement.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.identity.api.EmailDigestMode;
import com.example.taskmanagement.identity.api.EmailPreferenceOperations;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.integration.email.api.EmailOperations;
import com.example.taskmanagement.integration.email.api.EmailSender;
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
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import java.time.LocalDate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.mail.host=localhost",
        "mail.from=projectflow@example.com",
        "mail.app-public-base=https://app.example.com"
})
@Tag("integration")
class EmailIntegrationTest {
    private static final String PASSWORD = "email-test-passphrase";

    static class InterleavingSender extends RecordingEmailSender {
        private Runnable duringSend;

        @Override
        public void send(String to, String subject, String textBody, String htmlBody) {
            super.send(to, subject, textBody, htmlBody);
            if (duringSend != null) {
                var callback = duringSend;
                duringSend = null;
                callback.run();
            }
        }
    }

    @TestConfiguration
    static class EmailTestBeans {
        @Bean
        @Primary
        EmailSender recordingEmailSender() {
            return new InterleavingSender();
        }
    }

    @Autowired private EmailOperations email;
    @Autowired private EmailPreferenceOperations preferences;
    @Autowired private EmailSender sender;
    @Autowired private TaskOperations tasks;
    @Autowired private ProjectOperations projects;
    @Autowired private IdentityOperations identity;
    @Autowired private TokenService tokens;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private UserView owner;
    private UserView member;
    private ProjectView project;
    private TaskView task;
    private RecordingEmailSender mail;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        mail = (RecordingEmailSender) sender;
        mail.clear();
        ((InterleavingSender) mail).duringSend = null;
        owner = register("mail_owner");
        member = register("mail_member");
        project = projects.create(owner.id(), new ProjectData("Project", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "UTC"));
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        task = tasks.create(owner.id(), project.id(),
                new TaskData("Assigned", "", TaskPriority.HIGH, 15, null, member.id(), TaskBucket.WORK));
    }

    @Test
    void assignmentEmailAndMute() throws Exception {
        assertThat(email.deliverPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(mail.sent()).anyMatch(item -> item.to().equals(member.email())
                && item.subject().contains("Назначена")
                && item.body().contains("https://app.example.com/#/tasks/")
                && item.htmlBody() != null && item.htmlBody().contains("<!DOCTYPE html>"));

        preferences.setEnabled(member.id(), false);
        mail.clear();
        task = tasks.execute(owner.id(), task.id(), Command.START, task.version());
        email.deliverPending(20);
        task = tasks.execute(member.id(), task.id(), Command.COMPLETE, task.version());
        assertThat(email.deliverPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(mail.sent()).anyMatch(item -> item.to().equals(owner.email())
                && item.subject().contains("завершена"));
        assertThat(mail.sent()).noneMatch(item -> item.to().equals(member.email()));

        mvc.perform(get("/api/email").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(put("/api/email/enabled").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void digestRetainsEntriesCommittedWhileTheSelectedBatchIsBeingSent() {
        preferences.setDigestMode(member.id(), EmailDigestMode.DAILY);
        email.deliverPending(100);
        var independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ((InterleavingSender) mail).duringSend = () -> independent.executeWithoutResult(status -> jdbc.update(
                "insert into task_management.email_digest_entries "
                        + "(user_id, subject_line, body_line, created_at) values (?, ?, ?, current_timestamp)",
                member.id(), "Later event", "Committed during dispatch"));

        assertThat(email.flushDigests()).isEqualTo(1);
        assertThat(mail.sent()).hasSize(1).allMatch(item -> !item.body().contains("Committed during dispatch"));
        assertThat(email.status(member.id()).pendingDigestLines()).isEqualTo(1);
        assertThat(email.flushDigests()).isEqualTo(1);
        assertThat(mail.sent()).hasSize(2).anyMatch(item -> item.body().contains("Committed during dispatch"));
        assertThat(email.status(member.id()).pendingDigestLines()).isZero();
    }

    @Test
    void dailyDigestQueuesThenFlushes() throws Exception {
        preferences.setDigestMode(member.id(), EmailDigestMode.DAILY);
        mail.clear();
        assertThat(email.deliverPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(mail.sent()).noneMatch(item -> item.to().equals(member.email()));
        assertThat(email.status(member.id()).pendingDigestLines()).isGreaterThanOrEqualTo(1);

        assertThat(email.flushDigests()).isGreaterThanOrEqualTo(1);
        assertThat(mail.sent()).anyMatch(item -> item.to().equals(member.email())
                && item.subject().contains("дайджест")
                && item.body().contains("Назначена"));
        assertThat(email.status(member.id()).pendingDigestLines()).isZero();

        mvc.perform(put("/api/email/digest").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"IMMEDIATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.digestMode").value("IMMEDIATE"));
    }

    private UserView register(String name) {
        return identity.register(new RegisterUser(name, name + "@example.com", PASSWORD, "E", "Mail"));
    }

    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(identity.login(user.username(), PASSWORD)).accessToken();
    }
}
