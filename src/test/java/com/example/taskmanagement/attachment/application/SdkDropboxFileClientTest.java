package com.example.taskmanagement.attachment.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DbxUserFilesRequests;
import com.dropbox.core.v2.files.DeleteError;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.LookupError;
import com.example.taskmanagement.attachment.api.AttachmentException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SdkDropboxFileClientTest {
    private final DbxClientV2 client = mock(DbxClientV2.class);
    private final DbxUserFilesRequests files = mock(DbxUserFilesRequests.class);
    private final SdkDropboxFileClient adapter = new SdkDropboxFileClient(client);

    @BeforeEach
    void transport() {
        when(client.files()).thenReturn(files);
    }

    @Test
    void reportsTransportFailureWithoutProviderDiagnostics() throws Exception {
        var failure = mock(DbxException.class);
        when(failure.getMessage()).thenReturn("private-provider-diagnostic");
        when(files.deleteV2("id:stored")).thenThrow(failure);
        assertThatThrownBy(() -> adapter.delete("/unused", "id:stored"))
                .isInstanceOf(AttachmentException.class).hasMessage("STORAGE_ERROR").hasNoCause();
    }

    @Test
    void treatsAnAlreadyMissingRemoteFileAsDeleted() throws Exception {
        when(files.deleteV2("id:stored"))
                .thenThrow(failure(DeleteError.pathLookup(LookupError.NOT_FOUND)));
        assertThatCode(() -> adapter.delete("/unused", "id:stored")).doesNotThrowAnyException();
    }

    @Test
    void doesNotConfuseRestrictedContentOrRateLimitsWithNotFound() throws Exception {
        for (DeleteError error : List.of(DeleteError.pathLookup(LookupError.RESTRICTED_CONTENT),
                DeleteError.TOO_MANY_WRITE_OPERATIONS, DeleteError.OTHER)) {
            when(files.deleteV2("id:stored")).thenThrow(failure(error));
            assertThatThrownBy(() -> adapter.delete("/unused", "id:stored"))
                    .isInstanceOf(AttachmentException.class).hasMessage("STORAGE_ERROR").hasNoCause();
        }
    }

    @Test
    void deletesByStableProviderIdWhenAvailable() throws Exception {
        adapter.delete("/old-path", "id:stored");
        verify(files).deleteV2("id:stored");
    }

    @Test
    void retainsPathFallbackForLegacyReferences() throws Exception {
        adapter.delete("/legacy-path", null);
        verify(files).deleteV2("/legacy-path");
    }

    private static DeleteErrorException failure(DeleteError error) {
        return new DeleteErrorException("test-request-id", "private-provider-diagnostic", null, error);
    }
}
