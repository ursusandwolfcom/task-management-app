package com.example.taskmanagement.integration.telegram.api;

public final class TelegramException extends RuntimeException {
    public enum Reason {
        DISABLED, VALIDATION_ERROR, DELIVERY_FAILED, FORBIDDEN
    }

    private final Reason reason;

    public TelegramException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
