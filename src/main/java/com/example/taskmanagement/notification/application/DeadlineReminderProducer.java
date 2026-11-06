package com.example.taskmanagement.notification.application;

import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxOperations;
import com.example.taskmanagement.notification.domain.DeadlineReminderPolicy;
import com.example.taskmanagement.notification.persistence.DeadlineReminderEntity;
import com.example.taskmanagement.notification.persistence.DeadlineReminderRepository;
import com.example.taskmanagement.task.api.TaskDeadlineOperations;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DeadlineReminderProducer {
    private final TaskDeadlineOperations tasks;
    private final DeadlineReminderRepository reminders;
    private final OutboxOperations outbox;
    private final ObjectMapper json;
    private final Clock clock;
    private final DeadlineReminderPolicy policy = new DeadlineReminderPolicy();

    public DeadlineReminderProducer(TaskDeadlineOperations tasks, DeadlineReminderRepository reminders,
            OutboxOperations outbox, ObjectMapper json, Clock clock) {
        this.tasks = tasks;
        this.reminders = reminders;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public int produce(long taskId) {
        var candidate = tasks.lockCandidate(taskId);
        if (candidate.isEmpty()) {
            return 0;
        }
        var task = candidate.get();
        int created = 0;
        for (var reminder : policy.reminders(clock.instant(), ZoneId.of(task.timezone()), task.dueDate(),
                task.assigneeId(), task.ownerId(), task.eligible())) {
            if (reminders.existsByTaskIdAndDueDateAndRecipientIdAndKind(taskId, reminder.dueDate(),
                    reminder.recipientId(), reminder.kind().name())) {
                continue;
            }
            reminders.saveAndFlush(new DeadlineReminderEntity(taskId, reminder));
            outbox.append(OutboxEvents.DEADLINE_REMINDER, json.writeValueAsString(Map.of(
                    "taskId", taskId, "projectId", task.projectId(), "taskName", task.taskName(),
                    "dueDate", reminder.dueDate().toString(), "kind", reminder.kind().name(),
                    "recipientId", reminder.recipientId())));
            created++;
        }
        return created;
    }
}
