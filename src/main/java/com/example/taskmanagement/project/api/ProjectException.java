package com.example.taskmanagement.project.api;

/** Stable project diagnostics without persistence or HTTP coupling. */
public final class ProjectException extends RuntimeException {
    public enum Reason {
        PROJECT_NOT_FOUND, FORBIDDEN, VERSION_CONFLICT, VALIDATION_ERROR,
        MEMBER_ALREADY_EXISTS, MEMBER_NOT_FOUND, OWNER_REMOVAL_FORBIDDEN, MEMBER_HAS_ASSIGNMENTS, PROJECT_NOT_EMPTY
    }

    private final Reason reason;

    public ProjectException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
