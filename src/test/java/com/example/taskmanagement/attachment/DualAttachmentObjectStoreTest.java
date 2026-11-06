package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.taskmanagement.attachment.application.DualAttachmentObjectStore;
import com.example.taskmanagement.attachment.application.LocalAttachmentObjectStore;
import com.example.taskmanagement.attachment.application.AttachmentObjectStore;
import com.example.taskmanagement.attachment.api.AttachmentException;
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
    void propagatesRemoteFailureWithoutDeletingTheLegacyCopy() {
        var remote = mock(AttachmentObjectStore.class);
        var local = mock(AttachmentObjectStore.class);
        var failure = new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        doThrow(failure).when(remote).delete("file", "id:stored");
        assertThatThrownBy(() -> new DualAttachmentObjectStore(remote, local).delete("file", "id:stored"))
                .isSameAs(failure);
        verifyNoInteractions(local);
    }

    @Test
    void deletesLocalMetadataOnlyThroughTheLocalProvider() {
        var remote = mock(AttachmentObjectStore.class);
        var local = mock(AttachmentObjectStore.class);
        new DualAttachmentObjectStore(remote, local).delete("file", null);
        verify(local).delete("file", null);
        verifyNoInteractions(remote);
    }

    @Test
    void propagatesFailureOfTheAuthoritativeLocalFile() {
        var remote = mock(AttachmentObjectStore.class);
        var local = mock(AttachmentObjectStore.class);
        var failure = new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        doThrow(failure).when(local).delete("file", null);
        assertThatThrownBy(() -> new DualAttachmentObjectStore(remote, local).delete("file", null))
                .isSameAs(failure);
        verifyNoInteractions(remote);
    }

    @Test
    void secondaryLocalCleanupDoesNotUndoConfirmedRemoteDeletion() {
        var remote = mock(AttachmentObjectStore.class);
        var local = mock(AttachmentObjectStore.class);
        doThrow(new AttachmentException(AttachmentException.Reason.STORAGE_ERROR)).when(local).delete("file", null);
        assertThatCode(() -> new DualAttachmentObjectStore(remote, local).delete("file", "id:stored"))
                .doesNotThrowAnyException();
        verify(remote).delete("file", "id:stored");
        verify(local).delete("file", null);
    }

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
