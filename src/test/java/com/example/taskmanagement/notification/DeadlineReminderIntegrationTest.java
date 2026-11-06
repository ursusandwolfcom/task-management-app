package com.example.taskmanagement.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.identity.api.EmailDigestMode;
import com.example.taskmanagement.identity.api.EmailPreferenceOperations;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.TelegramAccountOperations;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.integration.email.RecordingEmailSender;
import com.example.taskmanagement.integration.email.api.EmailOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxOperations;
import com.example.taskmanagement.integration.outbox.application.OutboxService;
import com.example.taskmanagement.integration.telegram.RecordingTelegramBotClient;
import com.example.taskmanagement.integration.telegram.api.TelegramOperations;
import com.example.taskmanagement.notification.api.NotificationOperations;
import com.example.taskmanagement.notification.application.DeadlineReminderProducer;
import com.example.taskmanagement.notification.application.DeadlineReminderScheduler;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskDeadlineOperations;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskLifecyclePolicy.Command;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.mail.host=localhost", "telegram.bot-token=fixture-only"})
@Tag("integration")
class DeadlineReminderIntegrationTest {
    static class TestClock extends Clock {
        private volatile Instant instant = Instant.parse("2026-11-05T10:00:00Z");

        void at(String value) {
            instant = Instant.parse(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    static class FailingOutbox implements OutboxOperations {
        private final OutboxService delegate;
        private boolean fail;

        FailingOutbox(OutboxService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void append(String eventType, String payload) {
            if (fail && OutboxEvents.DEADLINE_REMINDER.equals(eventType)) {
                throw new IllegalStateException("Synthetic outbox failure");
            }
            delegate.append(eventType, payload);
        }
    }

    @TestConfiguration
    static class Beans {
        @Bean
        @Primary
        TestClock reminderTestClock() {
            return new TestClock();
        }

        @Bean
        @Primary
        FailingOutbox failingOutbox(OutboxService service) {
            return new FailingOutbox(service);
        }

        @Bean
        @Primary
        RecordingEmailSender reminderEmailSender() {
            return new RecordingEmailSender();
        }

        @Bean
        @Primary
        RecordingTelegramBotClient reminderBot() {
            return new RecordingTelegramBotClient();
        }
    }

    @Autowired private DeadlineReminderProducer producer;
    @Autowired private TaskDeadlineOperations deadlineTasks;
    @Autowired private TaskOperations tasks;
    @Autowired private ProjectOperations projects;
    @Autowired private IdentityOperations identity;
    @Autowired private NotificationOperations notifications;
    @Autowired private EmailOperations email;
    @Autowired private EmailPreferenceOperations preferences;
    @Autowired private TelegramOperations telegram;
    @Autowired private TelegramAccountOperations accounts;
    @Autowired private RecordingEmailSender mail;
    @Autowired private RecordingTelegramBotClient bot;
    @Autowired private FailingOutbox outbox;
    @Autowired private TestClock clock;
    @Autowired private JdbcTemplate jdbc;
    private UserView owner;
    private UserView member;
    private ProjectView project;
    private TaskView task;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        clock.at("2026-11-05T10:00:00Z");
        outbox.fail = false;
        mail.clear();
        bot.clear();
        owner = register("reminder_owner");
        member = register("reminder_member");
        project = projects.create(owner.id(), new ProjectData("Reminders", "", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), "Europe/Kyiv"));
        project = projects.addMember(owner.id(), project.id(), member.id(), project.version());
        task = tasks.create(owner.id(), project.id(), data(LocalDate.of(2026, 11, 6), member.id()));
        email.deliverPending(100);
        telegram.deliverPending(100);
        mail.clear();
    }

    @Test
    void concurrentScansCommitOneKeyAndEventPerRecipientAndKind() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return producer.produce(task.id());
            });
            var second = executor.submit(() -> {
                start.await();
                return producer.produce(task.id());
            });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isEqualTo(2);
        }
        assertThat(producer.produce(task.id())).isZero();
        assertThat(reminderKeys()).isEqualTo(2);
        assertThat(reminderEvents()).isEqualTo(2);
        clock.at("2026-11-06T07:00:00Z");
        assertThat(producer.produce(task.id())).isEqualTo(2);
        clock.at("2026-11-07T10:00:00Z");
        assertThat(producer.produce(task.id())).isZero();
        assertThat(reminderEvents()).isEqualTo(4);
    }

    @Test
    void outboxFailureRollsBackTheReminderKeySoTheSameScanCanRetry() {
        outbox.fail = true;
        assertThatThrownBy(() -> producer.produce(task.id())).isInstanceOf(IllegalStateException.class);
        assertThat(reminderKeys()).isZero();
        assertThat(reminderEvents()).isZero();
        outbox.fail = false;
        assertThat(producer.produce(task.id())).isEqualTo(2);
        assertThat(reminderEvents()).isEqualTo(2);
    }

    @Test
    void dueDateAndRecipientEditsHaveNewKeysButRestoringOldValuesDoesNotRepeat() {
        assertThat(producer.produce(task.id())).isEqualTo(2);
        task = tasks.replace(owner.id(), task.id(), data(LocalDate.of(2026, 11, 5), member.id()), task.version());
        assertThat(producer.produce(task.id())).isEqualTo(2);
        task = tasks.replace(owner.id(), task.id(), data(LocalDate.of(2026, 11, 6), member.id()), task.version());
        assertThat(producer.produce(task.id())).isZero();
        var other = register("reminder_other");
        project = projects.read(owner.id(), project.id());
        project = projects.addMember(owner.id(), project.id(), other.id(), project.version());
        task = tasks.replace(owner.id(), task.id(), data(task.dueDate(), other.id()), task.version());
        assertThat(producer.produce(task.id())).isEqualTo(1);
        task = tasks.replace(owner.id(), task.id(), data(task.dueDate(), member.id()), task.version());
        assertThat(producer.produce(task.id())).isZero();
        assertThat(reminderKeys()).isEqualTo(5);
    }

    @Test
    void boundedSchedulerMovesPastAlreadyProducedRowsAndWrapsAfterTheLastCandidate() {
        var second = tasks.create(owner.id(), project.id(), data(task.dueDate(), member.id()));
        var scheduler = new DeadlineReminderScheduler(deadlineTasks, producer, 1);
        scheduler.scan();
        assertThat(reminderKeys()).isEqualTo(2);
        scheduler.scan();
        assertThat(reminderKeys()).isEqualTo(4);
        scheduler.scan();
        scheduler.scan();
        assertThat(producer.produce(second.id())).isZero();
        assertThat(reminderKeys()).isEqualTo(4);
    }

    @Test
    void missingAssigneeDeadlineAndTerminalTasksAreSkippedWhileFailedTasksRemainEligible() {
        var unassigned = tasks.create(owner.id(), project.id(), data(task.dueDate(), null));
        var undated = tasks.create(owner.id(), project.id(), data(null, member.id()));
        assertThat(producer.produce(unassigned.id())).isZero();
        assertThat(producer.produce(undated.id())).isZero();
        task = tasks.execute(owner.id(), task.id(), Command.START, task.version());
        task = tasks.execute(member.id(), task.id(), Command.FAIL, task.version());
        assertThat(producer.produce(task.id())).isEqualTo(2);
        var completed = tasks.create(owner.id(), project.id(), data(task.dueDate(), member.id()));
        completed = tasks.execute(owner.id(), completed.id(), Command.START, completed.version());
        completed = tasks.execute(member.id(), completed.id(), Command.COMPLETE, completed.version());
        assertThat(producer.produce(completed.id())).isZero();
        var cancelled = tasks.create(owner.id(), project.id(), data(task.dueDate(), member.id()));
        cancelled = tasks.execute(owner.id(), cancelled.id(), Command.CANCEL, cancelled.version());
        assertThat(producer.produce(cancelled.id())).isZero();
    }

    @Test
    void allChannelsUseTheStoredRecipientAndRecheckCurrentAssignmentAndOwner() {
        accounts.bindChat(accounts.createLinkTicket(owner.id()).token(), 1001);
        accounts.bindChat(accounts.createLinkTicket(member.id()).token(), 1002);
        assertThat(producer.produce(task.id())).isEqualTo(2);
        task = tasks.replace(owner.id(), task.id(), data(task.dueDate(), owner.id()), task.version());
        notifications.projectPending(100);
        email.deliverPending(100);
        telegram.deliverPending(100);
        assertThat(mail.sent()).hasSize(1).allMatch(sent -> sent.to().equals(owner.email()));
        assertThat(bot.sent()).hasSize(1).allMatch(sent -> sent.chatId() == 1001);
        assertThat(notifications.list(member.id(), false, 0, 100).content())
                .noneMatch(item -> item.eventType().equals(OutboxEvents.DEADLINE_REMINDER));
        assertThat(notifications.list(owner.id(), false, 0, 100).content())
                .filteredOn(item -> item.eventType().equals(OutboxEvents.DEADLINE_REMINDER)).hasSize(1);
    }

    @Test
    void deferredDigestRechecksEligibilityAndMutedChannelsDoNotSend() {
        preferences.setDigestMode(member.id(), EmailDigestMode.DAILY);
        preferences.setEnabled(owner.id(), false);
        assertThat(producer.produce(task.id())).isEqualTo(2);
        email.deliverPending(100);
        assertThat(mail.sent()).isEmpty();
        assertThat(email.status(member.id()).pendingDigestLines()).isEqualTo(1);
        task = tasks.replace(owner.id(), task.id(), data(task.dueDate(), owner.id()), task.version());
        assertThat(email.flushDigests()).isZero();
        assertThat(email.status(member.id()).pendingDigestLines()).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void aFormerOwnerWithRevokedMembershipDoesNotReceiveTheQueuedReminder() {
        accounts.bindChat(accounts.createLinkTicket(owner.id()).token(), 1001);
        accounts.bindChat(accounts.createLinkTicket(member.id()).token(), 1002);
        assertThat(producer.produce(task.id())).isEqualTo(2);
        project = projects.read(owner.id(), project.id());
        project = projects.transferOwner(owner.id(), project.id(), member.id(), project.version());
        projects.removeMember(member.id(), project.id(), owner.id(), project.version());
        notifications.projectPending(100);
        email.deliverPending(100);
        telegram.deliverPending(100);
        assertThat(mail.sent()).hasSize(1).allMatch(sent -> sent.to().equals(member.email()));
        assertThat(bot.sent()).hasSize(1).allMatch(sent -> sent.chatId() == 1002);
        assertThat(notifications.list(owner.id(), false, 0, 100).content())
                .noneMatch(item -> item.eventType().equals(OutboxEvents.DEADLINE_REMINDER));
    }

    @Test
    void ownerAsAssigneeCreatesOneEventAndCompletionSuppressesQueuedDelivery() {
        task = tasks.replace(owner.id(), task.id(), data(task.dueDate(), owner.id()), task.version());
        assertThat(producer.produce(task.id())).isEqualTo(1);
        task = tasks.execute(owner.id(), task.id(), Command.START, task.version());
        tasks.execute(owner.id(), task.id(), Command.COMPLETE, task.version());
        notifications.projectPending(100);
        email.deliverPending(100);
        assertThat(mail.sent()).noneMatch(sent -> sent.subject().startsWith("Task deadline:"));
        assertThat(notifications.list(owner.id(), false, 0, 100).content())
                .noneMatch(item -> item.eventType().equals(OutboxEvents.DEADLINE_REMINDER));
    }

    @Test
    void schedulerContinuesAfterAFailedCandidateAndRetriesItAfterWrapping() {
        var second = tasks.create(owner.id(), project.id(), data(task.dueDate(), member.id()));
        var scheduler = new DeadlineReminderScheduler(deadlineTasks, producer, 100);
        outbox.fail = true;
        scheduler.scan();
        assertThat(reminderEvents()).isZero();
        outbox.fail = false;
        scheduler.scan();
        scheduler.scan();
        assertThat(reminderEvents()).isEqualTo(4);
        assertThat(producer.produce(second.id())).isZero();
    }

    private long reminderKeys() {
        return jdbc.queryForObject("select count(*) from task_management.deadline_reminders", Long.class);
    }

    private long reminderEvents() {
        return jdbc.queryForObject("select count(*) from task_management.outbox_messages where event_type = ?",
                Long.class, OutboxEvents.DEADLINE_REMINDER);
    }

    private TaskData data(LocalDate dueDate, Long assignee) {
        return new TaskData("Deadline task", "", TaskPriority.HIGH, 15, dueDate, assignee, TaskBucket.WORK);
    }

    private UserView register(String name) {
        return identity.register(new RegisterUser(name, name + "@example.test", "reminder-fixture-passphrase",
                "Reminder", "Fixture"));
    }
}
