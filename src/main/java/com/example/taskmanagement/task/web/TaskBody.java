package com.example.taskmanagement.task.web;

import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.shared.web.JsonFields;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Strict field parsing: explicit null clears optional metadata; omission retains it only for PATCH. */
final class TaskBody {
    private static final Set<String> FIELDS = Set.of("projectId", "name", "description", "priority",
            "estimatedMinutes", "dueDate", "assigneeId", "version");

    private TaskBody() {
    }

    static void validate(JsonNode body, Set<String> allowed) {
        new JsonFields(body, TaskBody::invalid).only(allowed);
    }

    static TaskData data(JsonNode body, TaskView previous) {
        var fields = new JsonFields(body, TaskBody::invalid).only(FIELDS);
        String name = fields.text("name", previous == null ? null : previous.name());
        String description = fields.text("description", previous == null ? null : previous.description());
        String priority = fields.text("priority", previous == null ? null : previous.priority().name());
        String due = fields.text("dueDate", previous == null || previous.dueDate() == null
                ? null : previous.dueDate().toString());
        Long estimate = fields.number("estimatedMinutes", previous == null || previous.estimatedMinutes() == null
                ? null : previous.estimatedMinutes().longValue());
        Long assignee = fields.number("assigneeId", previous == null ? null : previous.assigneeId());
        try {
            return new TaskData(name, description, priority == null ? null : TaskPriority.valueOf(priority),
                    estimate == null ? null : Math.toIntExact(estimate),
                    due == null ? null : LocalDate.parse(due), assignee);
        } catch (IllegalArgumentException | ArithmeticException | DateTimeParseException invalidValue) {
            throw invalid();
        }
    }

    static long version(JsonNode body) {
        return requiredNumber(body, "version", 0);
    }

    static long requiredNumber(JsonNode body, String name, long minimum) {
        return new JsonFields(body, TaskBody::invalid).requiredNumber(name, minimum);
    }

    static long projectId(JsonNode body, Long pathId) {
        Long bodyId = new JsonFields(body, TaskBody::invalid).number("projectId", null);
        if (pathId != null && body.has("projectId") && !pathId.equals(bodyId)) {
            throw invalid();
        }
        Long resolved = pathId == null ? bodyId : pathId;
        if (resolved == null || resolved <= 0) {
            throw invalid();
        }
        return resolved;
    }

    static void immutableProject(JsonNode body, long projectId) {
        if (body.has("projectId")) {
            projectId(body, projectId);
        }
    }

    private static TaskException invalid() {
        return new TaskException(TaskException.Reason.VALIDATION_ERROR);
    }
}
