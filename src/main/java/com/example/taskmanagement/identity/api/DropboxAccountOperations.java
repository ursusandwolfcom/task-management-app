package com.example.taskmanagement.identity.api;

import java.time.Instant;
import java.util.Optional;

/** Per-user Dropbox OAuth credentials; file I/O stays in the attachment module. */
public interface DropboxAccountOperations {
    record DropboxStatus(boolean linked, String accountId, Instant linkedAt) {
    }

    record DropboxCredential(String refreshToken, String accountId) {
        @Override
        public String toString() {
            return "DropboxCredential[redacted]";
        }
    }

    DropboxStatus status(long userId);

    Optional<DropboxCredential> credential(long userId);

    void link(long userId, String refreshToken, String accountId);

    void unlink(long userId);
}
