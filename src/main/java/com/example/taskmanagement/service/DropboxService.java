package com.example.taskmanagement.service;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeleteErrorException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DropboxService {
    private final DbxClientV2 client;

    public DropboxService(DbxClientV2 client) {
        this.client = client;
    }

    public String upload(String filename, InputStream input) throws IOException {
        try {
            return client.files().uploadBuilder("/course-tasks/" + UUID.randomUUID() + "/" + filename)
                    .uploadAndFinish(input).getId();
        } catch (DbxException exception) {
            throw unavailable();
        }
    }

    public byte[] download(String id) throws IOException {
        try (var download = client.files().download(id)) {
            byte[] data = download.getInputStream().readNBytes(AttachmentLimits.MAX_BYTES + 1);
            if (data.length > AttachmentLimits.MAX_BYTES) {
                throw unavailable();
            }
            return data;
        } catch (DbxException exception) {
            throw unavailable();
        }
    }

    public void delete(String id) {
        try {
            client.files().deleteV2(id);
        } catch (DeleteErrorException exception) {
            if (!exception.errorValue.isPathLookup() || !exception.errorValue.getPathLookupValue().isNotFound()) {
                throw unavailable();
            }
        } catch (DbxException exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dropbox operation failed");
    }
}
