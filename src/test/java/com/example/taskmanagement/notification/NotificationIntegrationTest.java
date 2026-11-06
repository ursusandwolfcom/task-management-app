package com.example.taskmanagement.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.attachment.api.AttachmentOperations;
import com.example.taskmanagement.collaboration.api.CommentData;
import com.example.taskmanagement.collaboration.api.CommentOperations;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.notification.api.NotificationOperations;
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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.example.taskmanagement.integration.outbox.api.OutboxConsumerOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
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
class NotificationIntegrationTest {
    private static final String PASSWORD = "notify-test-passphrase";
    @Autowired private NotificationOperations notifications;
    @Autowired private CommentOperations comments;
    @Autowired private AttachmentOperations attachments;
    @Autowired private TaskOperations tasks;
    @Autowired private ProjectOperations projects;
    @Autowired private IdentityOperations identity;
    @Autowired private TokenService tokens;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OutboxConsumerOperations outbox;
    @Autowired private PlatformTransactionManager transactionManager;
    private UserView owner;
    private UserView member;
    private ProjectView project;
    private TaskView task;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        owner = register("notify_owner");
        member = register("notify_member");
        project = projects.create(owner.id(), new ProjectData("Project", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "UTC"));
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        task = tasks.create(owner.id(), project.id(),
                new TaskData("Assigned", "", TaskPriority.HIGH, 15, null, member.id(), TaskBucket.WORK));
    }

    @Test
    void claimsRequireTheConsumersTransactionForEveryChannel() {
        assertThatThrownBy(() -> outbox.claimUnprocessed(100))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> outbox.claimTelegramUnprocessed(100))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> outbox.claimEmailUnprocessed(100))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void concurrentConsumersHoldClaimsUntilAcknowledgementCommits() throws Exception {
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                outbox.claimUnprocessed(100);
                claimed.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Consumer was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                var pending = outbox.claimUnprocessed(100);
                pending.forEach(message -> outbox.markProcessed(message.id()));
                return pending.size();
            }));
            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                started.countDown();
                return notifications.projectPending(100);
            });
            try {
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isPositive();
            assertThat(second.get(10, TimeUnit.SECONDS)).isZero();
        }
        assertThat(notifications.list(member.id(), false, 0, 100).content()).isEmpty();
    }

    @Test
    void assignmentAndCommentProjectThroughOutbox() throws Exception {
        assertThat(notifications.projectPending(20)).isGreaterThanOrEqualTo(1);
        var page = notifications.list(member.id(), true, 0, 20);
        assertThat(page.unreadCount()).isGreaterThanOrEqualTo(1);
        assertThat(page.content().getFirst().eventType()).isEqualTo("task.assigned");

        comments.create(owner.id(), new CommentData(task.id(), "Please review"));
        assertThat(notifications.projectPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(notifications.list(member.id(), true, 0, 20).content())
                .anyMatch(item -> item.eventType().equals("comment.created"));

        mvc.perform(get("/api/notifications").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        notifications.markAllRead(member.id());
        assertThat(notifications.list(member.id(), true, 0, 20).unreadCount()).isZero();

        mvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(member)))
                .andExpect(status().isNoContent());
    }

    @Test
    void startByOwnerNotifiesAssigneeAndCompleteByAssigneeNotifiesOwner() {
        notifications.projectPending(20);
        notifications.markAllRead(member.id());
        notifications.markAllRead(owner.id());

        task = tasks.execute(owner.id(), task.id(), Command.START, task.version());
        assertThat(notifications.projectPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(notifications.list(member.id(), true, 0, 20).content())
                .anyMatch(item -> item.eventType().equals("task.started"));

        task = tasks.execute(member.id(), task.id(), Command.COMPLETE, task.version());
        assertThat(notifications.projectPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(notifications.list(owner.id(), true, 0, 20).content())
                .anyMatch(item -> item.eventType().equals("task.completed"));
    }

    @Test
    void failByAssigneeNotifiesOwner() {
        notifications.projectPending(20);
        notifications.markAllRead(owner.id());
        task = tasks.execute(owner.id(), task.id(), Command.START, task.version());
        notifications.projectPending(20);
        notifications.markAllRead(owner.id());
        task = tasks.execute(member.id(), task.id(), Command.FAIL, task.version());
        assertThat(notifications.projectPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(notifications.list(owner.id(), true, 0, 20).content())
                .anyMatch(item -> item.eventType().equals("task.failed"));
    }

    @Test
    void attachmentUploadByOwnerNotifiesAssignee() {
        notifications.projectPending(20);
        notifications.markAllRead(member.id());
        byte[] bytes = "file".getBytes(StandardCharsets.UTF_8);
        attachments.upload(owner.id(), task.id(), "note.txt", "text/plain", bytes.length,
                new ByteArrayInputStream(bytes));
        assertThat(notifications.projectPending(20)).isGreaterThanOrEqualTo(1);
        assertThat(notifications.list(member.id(), true, 0, 20).content())
                .anyMatch(item -> item.eventType().equals("attachment.uploaded"));
    }

    private UserView register(String name) {
        return identity.register(new RegisterUser(name, name + "@example.com", PASSWORD, "First", "Last"));
    }

    private String bearer(UserView user) {
        return "Bearer " + tokens.issue(identity.login(user.username(), PASSWORD)).accessToken();
    }
}
