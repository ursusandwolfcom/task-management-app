package com.example.taskmanagement.attachment.api;

public final class MegaException extends RuntimeException {
    public enum Reason {
        MEGA_NOT_CONFIGURED, MEGA_AUTHENTICATION_FAILED, MEGA_MFA_REQUIRED,
        MEGA_ACCOUNT_UNAVAILABLE, MEGA_STORAGE_ERROR, MEGA_PROTOCOL_ERROR, MEGA_TIMEOUT, MEGA_BUSY
    }

    private final Reason reason;

    public MegaException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
