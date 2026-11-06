package com.example.taskmanagement.notification.application;

import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxMessage;
import com.example.taskmanagement.integration.outbox.api.OutboxRecipientGuard;
import com.example.taskmanagement.task.api.TaskDeadlineOperations;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DeadlineRecipientGuard implements OutboxRecipientGuard {
    private final TaskDeadlineOperations tasks;
    private final ObjectMapper json;

    public DeadlineRecipientGuard(TaskDeadlineOperations tasks, ObjectMapper json) {
        this.tasks = tasks;
        this.json = json;
    }

    @Override
    public boolean permits(OutboxMessage message, long recipientId) {
        if (!OutboxEvents.DEADLINE_REMINDER.equals(message.eventType())) {
            return true;
        }
        var payload = json.readTree(message.payload());
        if (payload.path("recipientId").asLong(0) != recipientId) {
            return false;
        }
        LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(payload.path("dueDate").asString(""));
        } catch (DateTimeParseException exception) {
            return false;
        }
        return tasks.mayReceive(payload.path("taskId").asLong(0), payload.path("projectId").asLong(0),
                dueDate, recipientId);
    }
}
