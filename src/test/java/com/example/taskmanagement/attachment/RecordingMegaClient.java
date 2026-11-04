package com.example.taskmanagement.attachment;

import com.example.taskmanagement.attachment.api.MegaClient;
import com.example.taskmanagement.attachment.api.MegaException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

class RecordingMegaClient implements MegaClient {
    final Map<String, byte[]> files = new HashMap<>();
    boolean failUpload;
    String downloadedAccount;
    int sequence;

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public Session login(String email, String password, String mfaCode, String folderName) {
        if (!"mega-password".equals(password)) {
            throw new MegaException(MegaException.Reason.MEGA_AUTHENTICATION_FAILED);
        }
        if (email.startsWith("mfa") && !"123456".equals(mfaCode)) {
            throw new MegaException(MegaException.Reason.MEGA_MFA_REQUIRED);
        }
        String account = email.startsWith("other") ? "accountB001" : "accountA001";
        return new Session(account, "sensitive-session-" + account, "folder00");
    }

    @Override
    public String upload(Session session, Path source, String key) {
        if (failUpload) {
            throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
        }
        String handle = "file" + String.format(java.util.Locale.ROOT, "%04d", ++sequence);
        try {
            files.put(session.accountId() + ":" + handle, Files.readAllBytes(source));
            return handle;
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Override
    public void download(Session session, String handle, Path destination) {
        downloadedAccount = session.accountId();
        byte[] bytes = files.get(session.accountId() + ":" + handle);
        if (bytes == null) {
            throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
        }
        try {
            Files.write(destination, bytes);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Override
    public void remove(Session session, String handle) {
        files.remove(session.accountId() + ":" + handle);
    }
}
