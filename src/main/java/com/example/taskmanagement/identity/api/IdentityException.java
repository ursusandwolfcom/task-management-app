package com.example.taskmanagement.identity.api;

/** Stable business error without HTTP coupling or sensitive input in its message. */
public final class IdentityException extends RuntimeException {
    public enum Reason {
        INVALID_CREDENTIALS, INVALID_SESSION, DUPLICATE_IDENTITY, USER_NOT_FOUND,
        FORBIDDEN, VERSION_CONFLICT, ADMIN_ALREADY_EXISTS, LAST_ADMIN,
        TELEGRAM_LINK_INVALID, TELEGRAM_CHAT_TAKEN, VALIDATION_ERROR, DROPBOX_ACCOUNT_TAKEN,
        DROPBOX_CREDENTIALS_NOT_CONFIGURED, DROPBOX_CREDENTIALS_UNAVAILABLE
    }

    private final Reason reason;

    public IdentityException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
