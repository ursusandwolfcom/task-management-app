package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.example.taskmanagement.attachment.api.MegaException;
import com.example.taskmanagement.attachment.application.NativeMegaClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeMegaClientTest {
    @TempDir Path directory;

    @Test
    void failsClosedWithoutConfiguredExecutable() {
        var client = new NativeMegaClient(true, directory.resolve("missing").toString(), "test-app-key", 1);
        assertThatThrownBy(() -> client.login("private@example.test", "private-password", "", "folder"))
                .isInstanceOf(MegaException.class).hasMessage("MEGA_NOT_CONFIGURED");
    }

    @Test
    void terminatesStalledNativeProcessAtDeadline() throws Exception {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        Path binary = script("#!/bin/sh\nexec sleep 30\n");
        var client = new NativeMegaClient(true, binary.toString(), "test-app-key", 1);
        assertThatThrownBy(() -> client.login("private@example.test", "private-password", "", "folder"))
                .isInstanceOf(MegaException.class).hasMessage("MEGA_TIMEOUT");
    }

    @Test
    void rejectsInvalidNativeFrameWithoutReturningSensitiveDiagnostics() throws Exception {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        Path binary = script("#!/bin/sh\ncat >/dev/null\nprintf 'not-a-protocol-frame'\n");
        var client = new NativeMegaClient(true, binary.toString(), "test-app-key", 3);
        assertThatThrownBy(() -> client.login("private@example.test", "private-password", "", "folder"))
                .isInstanceOf(MegaException.class).hasMessage("MEGA_PROTOCOL_ERROR");
    }

    private Path script(String body) throws Exception {
        Path binary = Files.writeString(directory.resolve("native-fixture"), body);
        Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwx------"));
        return binary;
    }
}
