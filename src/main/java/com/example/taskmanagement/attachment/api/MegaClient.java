package com.example.taskmanagement.attachment.api;

import java.nio.file.Path;

/** Official SDK transport; credentials never appear in command-line arguments or diagnostics. */
public interface MegaClient {
    record Session(String accountId, String session, String folderHandle) {
        @Override
        public String toString() {
            return "MegaSession[redacted]";
        }
    }

    boolean configured();

    Session login(String email, String password, String mfaCode, String folderName);

    String upload(Session session, Path source, String key);

    void download(Session session, String handle, Path destination);

    void remove(Session session, String handle);
}
