package com.example.taskmanagement.notification.api;

public final class NotificationException extends RuntimeException {
    public enum Reason {
        NOTIFICATION_NOT_FOUND, FORBIDDEN, VALIDATION_ERROR
    }

    private final Reason reason;

    public NotificationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
