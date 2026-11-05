package com.example.taskmanagement.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.MegaClient;
import com.example.taskmanagement.identity.api.DropboxAccountOperations;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MegaAwareObjectStoreFactoryTest {
    @TempDir Path directory;

    @Test
    void legacyLocalFilesIgnoreCurrentPersonalCredentialsThroughTheOuterFactory() throws Exception {
        var dropbox = mock(DropboxAccountOperations.class);
        doThrow(new AssertionError("Legacy local files must not need current personal credentials"))
                .when(dropbox).credential(7);
        var local = new LocalAttachmentObjectStore(directory.toString());
        var legacy = new DefaultAttachmentObjectStoreFactory(local, dropbox, "dropbox", "test-key", "test-secret",
                "/projectflow");
        var mega = mock(MegaAccountService.class);
        var client = mock(MegaClient.class);
        var factory = new MegaAwareObjectStoreFactory(legacy, mega, client);
        int index = 0;
        for (String externalId : new String[] {null, "", " "}) {
            String key = "legacy" + index++;
            byte[] bytes = {1, 2, 3};
            local.put(key, new ByteArrayInputStream(bytes), bytes.length);
            var selected = factory.forAttachment(7, externalId);
            try (var body = selected.open(key, externalId)) {
                assertThat(body.readAllBytes()).isEqualTo(bytes);
            }
            selected.delete(key, externalId);
            assertThat(directory.resolve(key)).doesNotExist();
        }
        verifyNoInteractions(dropbox, mega, client);
    }

    @Test
    void disabledDropboxRejectsCloudReferencesThroughTheOuterFactory() {
        var local = mock(AttachmentObjectStore.class);
        var dropbox = mock(DropboxAccountOperations.class);
        var mega = mock(MegaAccountService.class);
        var client = mock(MegaClient.class);
        var legacy = new DefaultAttachmentObjectStoreFactory(local, dropbox, "local", "", "", "/projectflow");
        var factory = new MegaAwareObjectStoreFactory(legacy, mega, client);
        assertThatThrownBy(() -> factory.forAttachment(7, "id:cloud-file"))
                .isInstanceOf(AttachmentException.class).hasMessage("STORAGE_ERROR");
        verifyNoInteractions(local, dropbox, mega, client);
    }

    @Test
    void megaReferencesStillUseTheOriginalAccountWithoutCallingLegacyStorage() {
        var legacy = mock(AttachmentObjectStoreFactory.class);
        var accounts = mock(MegaAccountService.class);
        var client = mock(MegaClient.class);
        var session = new MegaClient.Session("accountA001", "offline-session", "folderA001");
        when(accounts.configured()).thenReturn(true);
        when(accounts.credential(7)).thenReturn(Optional.of(session));
        var factory = new MegaAwareObjectStoreFactory(legacy, accounts, client);
        factory.forAttachment(7, "mega:accountA001:node0001").delete("opaque-key", "mega:accountA001:node0001");
        verify(client).remove(session, "node0001");
        verifyNoInteractions(legacy);
    }
}
