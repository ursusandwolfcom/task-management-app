package com.example.taskmanagement.attachment.api;

import java.time.Instant;

public interface MegaOperations {
    record Status(boolean configured, boolean linked, String accountId, Instant linkedAt) {
    }

    Status status(long userId);

    Status link(long userId, String email, String password, String mfaCode);

    /** Removes local credentials only; metadata and remote files are retained. */
    Status unlink(long userId);
}
