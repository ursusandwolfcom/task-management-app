package com.example.taskmanagement.collaboration.api;

public final class CollaborationException extends RuntimeException {
    public enum Reason {
        COMMENT_NOT_FOUND, TASK_NOT_FOUND, LABEL_NOT_FOUND, FORBIDDEN, VALIDATION_ERROR,
        LABEL_ALREADY_EXISTS, VERSION_CONFLICT, CROSS_PROJECT_LABEL
    }

    private final Reason reason;

    public CollaborationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
