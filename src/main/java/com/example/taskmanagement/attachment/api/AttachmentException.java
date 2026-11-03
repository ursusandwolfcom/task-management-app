package com.example.taskmanagement.attachment.api;

public final class AttachmentException extends RuntimeException {
    public enum Reason {
        ATTACHMENT_NOT_FOUND, TASK_NOT_FOUND, FORBIDDEN, VALIDATION_ERROR, STORAGE_ERROR,
        OAUTH_NOT_CONFIGURED, OAUTH_STATE_INVALID
    }

    private final Reason reason;

    public AttachmentException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
