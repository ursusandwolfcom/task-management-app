package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeleteError;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.LookupError;
import com.example.taskmanagement.service.DropboxService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DropboxServiceTest {
    private final DbxClientV2 client = mock(DbxClientV2.class, RETURNS_DEEP_STUBS);
    private final DropboxService service = new DropboxService(client);

    @Test
    void onlyProviderNotFoundIsAnIdempotentDeletion() throws Exception {
        when(client.files().deleteV2("id:gone")).thenThrow(new DeleteErrorException("delete", "request", null,
                DeleteError.pathLookup(LookupError.NOT_FOUND)));
        assertThatCode(() -> service.delete("id:gone")).doesNotThrowAnyException();
        when(client.files().deleteV2("id:locked")).thenThrow(new DeleteErrorException("delete", "request", null,
                DeleteError.pathLookup(LookupError.LOCKED)));
        assertThatThrownBy(() -> service.delete("id:locked")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("502");
        when(client.files().deleteV2("id:failed")).thenThrow(new DbxException("private provider detail"));
        assertThatThrownBy(() -> service.delete("id:failed")).isInstanceOf(ResponseStatusException.class)
                .hasMessageNotContaining("private provider detail");
    }

    @Test
    void uploadReturnsProviderIdAndDownloadBoundsTheActualBytes() throws Exception {
        var metadata = mock(FileMetadata.class);
        when(metadata.getId()).thenReturn("id:uploaded");
        when(client.files().uploadBuilder(anyString()).uploadAndFinish(any(InputStream.class))).thenReturn(metadata);
        assertThat(service.upload("notes.txt", new ByteArrayInputStream(new byte[] {1, 2})))
                .isEqualTo("id:uploaded");
        when(client.files().download("id:uploaded").getInputStream())
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2}));
        assertThat(service.download("id:uploaded")).containsExactly((byte) 1, (byte) 2);
        when(client.files().download("id:oversize").getInputStream())
                .thenReturn(new ByteArrayInputStream(new byte[5 * 1024 * 1024 + 1]));
        assertThatThrownBy(() -> service.download("id:oversize")).isInstanceOf(ResponseStatusException.class);
    }
}
