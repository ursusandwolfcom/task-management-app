package com.example.taskmanagement.integration.email.api;

public final class EmailException extends RuntimeException {
    public enum Reason {
        DISABLED, VALIDATION_ERROR, DELIVERY_FAILED
    }

    private final Reason reason;

    public EmailException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
