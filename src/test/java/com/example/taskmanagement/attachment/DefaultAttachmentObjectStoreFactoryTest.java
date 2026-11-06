package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.application.AttachmentObjectStore;
import com.example.taskmanagement.attachment.application.DefaultAttachmentObjectStoreFactory;
import com.example.taskmanagement.attachment.application.LocalAttachmentObjectStore;
import com.example.taskmanagement.identity.api.DropboxAccountOperations;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultAttachmentObjectStoreFactoryTest {
    @TempDir Path directory;

    @Test
    void oldLocalFilesDoNotRequireTheUploadersCurrentDropboxCredentials() throws Exception {
        var accounts = mock(DropboxAccountOperations.class);
        doThrow(new AssertionError("Local files must not use personal cloud credentials"))
                .when(accounts).credential(7);
        var local = new LocalAttachmentObjectStore(directory.toString());
        var factory = new DefaultAttachmentObjectStoreFactory(local, accounts, "dropbox", "test-key", "test-secret",
                "/projectflow");
        int index = 0;
        for (String externalId : new String[] {null, ""}) {
            String key = "legacy" + index++;
            local.put(key, new ByteArrayInputStream(new byte[] {1, 2}), 2);
            var selected = factory.forAttachment(7, externalId);
            try (var body = selected.open(key, externalId)) {
                assertThat(body.readAllBytes()).isEqualTo(new byte[] {1, 2});
            }
            selected.delete(key, externalId);
            assertThat(directory.resolve(key)).doesNotExist();
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void disabledDropboxNeverDeletesCloudMetadataThroughALocalFallback() {
        var local = mock(AttachmentObjectStore.class);
        var accounts = mock(DropboxAccountOperations.class);
        var factory = new DefaultAttachmentObjectStoreFactory(local, accounts, "local", "", "", "/projectflow");
        assertThatThrownBy(() -> factory.forAttachment(7, "id:remote"))
                .isInstanceOf(AttachmentException.class).hasMessage("STORAGE_ERROR");
        verifyNoInteractions(local, accounts);
    }
}
