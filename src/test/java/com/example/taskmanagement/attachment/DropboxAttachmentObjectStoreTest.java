package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.application.DropboxAttachmentObjectStore;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DropboxAttachmentObjectStoreTest {
    @Test
    void putOpenDeleteRoundTripStoresExternalId() throws Exception {
        var client = new RecordingDropboxFileClient();
        var store = new DropboxAttachmentObjectStore(client, "/projectflow");
        byte[] bytes = "dropbox-bytes".getBytes(StandardCharsets.UTF_8);
        String externalId = store.put("abc123", new ByteArrayInputStream(bytes), bytes.length);
        assertThat(externalId).startsWith("id:");
        try (var in = store.open("abc123", externalId)) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }
        store.delete("abc123", externalId);
        assertThat(client.size()).isZero();
    }

    @Test
    void rejectsUnsafeKeys() {
        var store = new DropboxAttachmentObjectStore(new RecordingDropboxFileClient(), "/projectflow");
        assertThatThrownBy(() -> store.put("../x", new ByteArrayInputStream(new byte[] {1}), 1))
                .isInstanceOf(AttachmentException.class)
                .extracting("reason")
                .isEqualTo(AttachmentException.Reason.STORAGE_ERROR);
    }
}
