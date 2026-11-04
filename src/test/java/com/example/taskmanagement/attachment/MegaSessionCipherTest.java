package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.attachment.api.MegaException;
import com.example.taskmanagement.attachment.application.MegaSessionCipher;
import org.junit.jupiter.api.Test;

class MegaSessionCipherTest {
    private final MegaSessionCipher cipher = new MegaSessionCipher(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Test
    void encryptsWithUniqueNoncesAndBindsSessionToItsUser() {
        String first = cipher.encrypt(1L, "private-session");
        String second = cipher.encrypt(1L, "private-session");
        assertThat(first).isNotEqualTo(second).doesNotContain("private-session");
        assertThat(cipher.decrypt(1L, first)).isEqualTo("private-session");
        assertThatThrownBy(() -> cipher.decrypt(2L, first)).isInstanceOf(MegaException.class)
                .extracting("reason").isEqualTo(MegaException.Reason.MEGA_ACCOUNT_UNAVAILABLE);
    }

    @Test
    void rejectsChangedPayloadAndMissingKeyWithoutExposingSession() {
        byte[] bytes = java.util.Base64.getDecoder().decode(cipher.encrypt(1L, "private-session"));
        bytes[bytes.length - 1] ^= 1;
        String tampered = java.util.Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> cipher.decrypt(1L, tampered)).isInstanceOf(MegaException.class)
                .hasMessage("MEGA_ACCOUNT_UNAVAILABLE");
        assertThatThrownBy(() -> new MegaSessionCipher("").encrypt(1L, "private-session"))
                .isInstanceOf(MegaException.class).hasMessage("MEGA_NOT_CONFIGURED");
    }
}
