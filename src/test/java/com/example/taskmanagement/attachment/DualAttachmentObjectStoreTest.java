package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.attachment.application.DualAttachmentObjectStore;
import com.example.taskmanagement.attachment.application.LocalAttachmentObjectStore;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class DualAttachmentObjectStoreTest {
    @TempDir
    Path temp;

    @Test
    void openFallsBackToLocalWhenNoExternalId() throws Exception {
        var local = new LocalAttachmentObjectStore(temp.toString());
        var dropbox = new RecordingDropboxFileClient();
        var dual = new DualAttachmentObjectStore(
                new com.example.taskmanagement.attachment.application.DropboxAttachmentObjectStore(dropbox,
                        "/projectflow"),
                local);
        byte[] bytes = "legacy".getBytes(StandardCharsets.UTF_8);
        local.put("legacy1", new ByteArrayInputStream(bytes), bytes.length);
        try (var in = dual.open("legacy1", null)) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }
        assertThat(Files.exists(temp.resolve("legacy1"))).isTrue();
    }
}
