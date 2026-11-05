package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.application.LocalAttachmentObjectStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalAttachmentObjectStoreTest {
    @TempDir Path directory;

    @Test
    void storesExactBytesAndSupportsDownloadAndIdempotentDelete() throws Exception {
        var store = store();
        byte[] bytes = {0, 1, (byte) 255};
        assertThat(store.put("file1", new ByteArrayInputStream(bytes), bytes.length)).isNull();
        try (var input = store.open("file1", null)) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }
        store.delete("file1", null);
        store.delete("file1", null);
        assertThat(directory.resolve("file1")).doesNotExist();
    }

    @Test
    void removesPartialFileWhenReadingFails() throws Exception {
        var body = new InputStream() {
            private int reads;

            @Override
            public int read() throws IOException {
                if (reads++ == 0) {
                    return 1;
                }
                throw new IOException("private-source-diagnostic");
            }
        };
        assertThatThrownBy(() -> store().put("failed", body, 2))
                .isInstanceOf(AttachmentException.class).hasMessage("STORAGE_ERROR").hasNoCause();
        assertThat(directory.resolve("failed")).doesNotExist();
    }

    @Test
    void removesPartialFileWhenTheSourceThrowsAnUncheckedException() throws Exception {
        var failure = new IllegalStateException("source failed");
        var body = new InputStream() {
            private int reads;

            @Override
            public int read() {
                if (reads++ == 0) {
                    return 1;
                }
                throw failure;
            }
        };
        assertThatThrownBy(() -> store().put("failed", body, 2)).isSameAs(failure);
        assertThat(directory.resolve("failed")).doesNotExist();
    }

    @Test
    void rejectsAnOversizedSourceAfterAtMostOneExtraByte() throws Exception {
        var body = new ByteArrayInputStream(new byte[100]);
        assertThatThrownBy(() -> store().put("oversized", body, 2))
                .isInstanceOf(AttachmentException.class).hasMessage("VALIDATION_ERROR");
        assertThat(body.available()).isEqualTo(97);
        assertThat(directory.resolve("oversized")).doesNotExist();
    }

    @Test
    void rejectsAShortSourceAndClosesTheFileBeforeCleanup() throws Exception {
        assertThatThrownBy(() -> store().put("short", new ByteArrayInputStream(new byte[] {1}), 2))
                .isInstanceOf(AttachmentException.class).hasMessage("VALIDATION_ERROR");
        assertThat(directory.resolve("short")).doesNotExist();
    }

    @Test
    void validatesDeclaredSizeBeforeReadingOrCreatingAFile() throws Exception {
        for (long size : new long[] {-1, 0, 5_242_881, Long.MAX_VALUE}) {
            var body = new ByteArrayInputStream(new byte[] {1});
            assertThatThrownBy(() -> store().put("invalid", body, size))
                    .isInstanceOf(AttachmentException.class).hasMessage("VALIDATION_ERROR");
            assertThat(body.available()).isEqualTo(1);
            assertThat(directory.resolve("invalid")).doesNotExist();
        }
    }

    @Test
    void neverOverwritesOrDeletesAPreviouslyStoredObject() throws Exception {
        byte[] original = {1, 2, 3};
        Files.write(directory.resolve("existing"), original);
        var body = new ByteArrayInputStream(new byte[] {4});
        assertThatThrownBy(() -> store().put("existing", body, 1))
                .isInstanceOf(AttachmentException.class).hasMessage("STORAGE_ERROR");
        assertThat(Files.readAllBytes(directory.resolve("existing"))).isEqualTo(original);
        assertThat(body.available()).isEqualTo(1);
    }

    @Test
    void rejectsTraversalAndLeavesStorageEmpty() throws Exception {
        assertThatThrownBy(() -> store().put("../outside", new ByteArrayInputStream(new byte[] {1}), 1))
                .isInstanceOf(AttachmentException.class).hasMessage("STORAGE_ERROR");
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void leavesTheCallerOwnedInputStreamOpen() throws Exception {
        var body = new ByteArrayInputStream(new byte[] {1}) {
            @Override
            public void close() {
                throw new AssertionError("The caller owns the input stream");
            }
        };
        store().put("file1", body, 1);
    }

    private LocalAttachmentObjectStore store() throws IOException {
        return new LocalAttachmentObjectStore(directory.toString());
    }
}
