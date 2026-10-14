package com.example.taskmanagement.task.web;

import com.example.taskmanagement.task.api.TaskData;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import java.time.LocalDate;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Strict field parsing: explicit null clears optional metadata; omission retains it only for PATCH. */
final class TaskBody {
    private static final Set<String> FIELDS = Set.of("projectId", "name", "description", "priority",
            "estimatedMinutes", "dueDate", "assigneeId", "version");

    private TaskBody() {
    }

    static void validate(JsonNode body, Set<String> allowed) {
        if (!body.isObject() || body.propertyNames().stream().anyMatch(name -> !allowed.contains(name))) {
            throw invalid();
        }
    }

    static TaskData data(JsonNode body, TaskView previous) {
        validate(body, FIELDS);
        String name = text(body, "name", previous == null ? null : previous.name());
        String description = text(body, "description", previous == null ? null : previous.description());
        String priority = text(body, "priority", previous == null ? null : previous.priority().name());
        String due = text(body, "dueDate", previous == null || previous.dueDate() == null
                ? null : previous.dueDate().toString());
        Long estimate = number(body, "estimatedMinutes", previous == null || previous.estimatedMinutes() == null
                ? null : previous.estimatedMinutes().longValue());
        Long assignee = number(body, "assigneeId", previous == null ? null : previous.assigneeId());
        try {
            return new TaskData(name, description, priority == null ? null : TaskPriority.valueOf(priority),
                    estimate == null ? null : Math.toIntExact(estimate),
                    due == null ? null : LocalDate.parse(due), assignee);
        } catch (IllegalArgumentException | ArithmeticException invalidValue) {
            throw invalid();
        }
    }

    static long version(JsonNode body) {
        Long version = number(body, "version", null);
        if (version == null || version < 0) {
            throw invalid();
        }
        return version;
    }

    static long projectId(JsonNode body, Long pathId) {
        Long bodyId = number(body, "projectId", null);
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

    private static String text(JsonNode body, String name, String fallback) {
        var node = body.get(name);
        if (node == null) {
            return fallback;
        }
        if (node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalid();
        }
        return node.asString();
    }

    private static Long number(JsonNode body, String name, Long fallback) {
        var node = body.get(name);
        if (node == null) {
            return fallback;
        }
        if (node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw invalid();
        }
        return node.longValue();
    }

    private static TaskException invalid() {
        return new TaskException(TaskException.Reason.VALIDATION_ERROR);
    }
}
