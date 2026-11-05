package com.example.taskmanagement.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.attachment.api.MegaException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeMegaResponseTest {
    @Test
    void acceptsEachSuccessfulOperationShape() throws Exception {
        assertThat(read(frame("OK", "account1", "private-session", "folder01"), 4))
                .containsExactly("OK", "account1", "private-session", "folder01");
        assertThat(read(frame("OK", "node0001"), 2)).containsExactly("OK", "node0001");
        assertThat(read(frame("OK"), 1)).containsExactly("OK");
    }

    @Test
    void acceptsSingleFieldErrorsForEveryOperation() throws Exception {
        for (int expectedFields : List.of(1, 2, 4)) {
            assertThat(read(frame("MEGA_MFA_REQUIRED"), expectedFields)).containsExactly("MEGA_MFA_REQUIRED");
        }
    }

    @Test
    void rejectsTruncationAtEveryByteBoundaryWithProtocolError() throws Exception {
        byte[] valid = frame("OK", "account1", "private-session", "folder01");
        for (int length = 0; length < valid.length; length++) {
            assertProtocolError(Arrays.copyOf(valid, length), 4);
        }
    }

    @Test
    void rejectsExtraFieldsOnDownloadAndRemoveAcknowledgements() throws Exception {
        assertProtocolError(frame("OK", "private-diagnostic"), 1);
    }

    @Test
    void rejectsWrongLoginAndUploadFieldCounts() throws Exception {
        assertProtocolError(frame("OK"), 4);
        assertProtocolError(frame("OK", "node0001", "private-diagnostic"), 2);
    }

    @Test
    void rejectsExtraFieldsOnErrorResponses() throws Exception {
        assertProtocolError(frame("MEGA_AUTHENTICATION_FAILED", "private-diagnostic"), 4);
    }

    @Test
    void rejectsInvalidUtf8InsteadOfReplacingIt() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(2);
            write(output, "OK");
            output.writeInt(2);
            output.write(new byte[] {(byte) 0xc3, 0x28});
        }
        assertProtocolError(bytes.toByteArray(), 2);
    }

    @Test
    void rejectsNulInResponseFields() throws Exception {
        assertProtocolError(frame("OK", "node\0hidden"), 2);
    }

    @Test
    void enforcesFieldCountAndLengthBounds() throws Exception {
        for (int count : List.of(-1, 0, 5, Integer.MAX_VALUE)) {
            assertProtocolError(integers(count), 1);
        }
        for (int length : List.of(-1, 16385, Integer.MAX_VALUE)) {
            assertProtocolError(integers(1, length), 1);
        }
        assertProtocolError(frame("OK", "x".repeat(16385)), 2);
    }

    @Test
    void acceptsMaximumFieldSizeAndValidUtf8() throws Exception {
        String value = "é".repeat(8192);
        assertThat(read(frame("OK", value), 2)).containsExactly("OK", value);
    }

    @Test
    void rejectsTrailingBytesAndConcatenatedFrames() throws Exception {
        byte[] valid = frame("OK");
        assertProtocolError(Arrays.copyOf(valid, valid.length + 1), 1);
        var bytes = new ByteArrayOutputStream();
        bytes.write(valid);
        bytes.write(valid);
        assertProtocolError(bytes.toByteArray(), 1);
    }

    private static List<String> read(byte[] bytes, int expectedFields) throws Exception {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return NativeMegaClient.readResponse(input, expectedFields);
        }
    }

    private static void assertProtocolError(byte[] bytes, int expectedFields) {
        assertThatThrownBy(() -> read(bytes, expectedFields)).isInstanceOf(MegaException.class)
                .hasMessage("MEGA_PROTOCOL_ERROR").hasNoCause();
    }

    private static byte[] frame(String... fields) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(fields.length);
            for (String field : fields) {
                write(output, field);
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] integers(int... values) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            for (int value : values) {
                output.writeInt(value);
            }
        }
        return bytes.toByteArray();
    }

    private static void write(DataOutputStream output, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
