package com.example.taskmanagement.task.api;

public final class TaskException extends RuntimeException {
    public enum Reason {
        TASK_NOT_FOUND, FORBIDDEN, VALIDATION_ERROR, VERSION_CONFLICT, ASSIGNEE_NOT_MEMBER,
        ASSIGNEE_REQUIRED, INVALID_TASK_TRANSITION, DEPENDENCIES_NOT_COMPLETED,
        ASSIGNMENT_CHANGE_FORBIDDEN, TASK_DELETE_FORBIDDEN, TASK_HAS_DEPENDENTS, GRAPH_TOO_LARGE
    }

    private final Reason reason;

    public TaskException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
