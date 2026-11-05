package com.example.taskmanagement.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.identity.application.DropboxCredentialCipher;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DropboxCredentialCipherTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private final DropboxCredentialCipher cipher = new DropboxCredentialCipher(KEY);

    @Test
    void usesIndependentNoncesAndAuthenticatesTheOwningUser() {
        String first = cipher.encrypt(7, "private-refresh-token");
        String second = cipher.encrypt(7, "private-refresh-token");
        assertThat(first).startsWith("v1:").isNotEqualTo(second).doesNotContain("private-refresh-token");
        assertThat(cipher.decrypt(7, first)).isEqualTo("private-refresh-token");
        assertThatThrownBy(() -> cipher.decrypt(8, first)).isInstanceOf(IdentityException.class)
                .hasMessage("DROPBOX_CREDENTIALS_UNAVAILABLE").hasNoCause();
    }

    @Test
    void rejectsChangedPayloadWithoutExposingCredentials() {
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt(7, "private-refresh-token").substring(3));
        bytes[bytes.length - 1] ^= 1;
        String changed = "v1:" + Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> cipher.decrypt(7, changed)).isInstanceOf(IdentityException.class)
                .hasMessage("DROPBOX_CREDENTIALS_UNAVAILABLE").hasNoCause();
    }

    @Test
    void neverAcceptsPlaintextUnknownVersionOrTruncatedEnvelopes() {
        for (String value : new String[] {null, "", "private-refresh-token", "v2:unknown", "v1:not-base64",
                "v1:" + Base64.getEncoder().encodeToString(new byte[27])}) {
            assertThatThrownBy(() -> cipher.decrypt(7, value)).isInstanceOf(IdentityException.class)
                    .hasMessage("DROPBOX_CREDENTIALS_UNAVAILABLE").hasNoCause();
        }
    }

    @Test
    void keyLossRequiresRelinkingRatherThanPlaintextFallback() {
        byte[] bytes = new byte[32];
        bytes[0] = 1;
        var changedKey = new DropboxCredentialCipher(Base64.getEncoder().encodeToString(bytes));
        String value = cipher.encrypt(7, "private-refresh-token");
        assertThatThrownBy(() -> changedKey.decrypt(7, value)).isInstanceOf(IdentityException.class)
                .hasMessage("DROPBOX_CREDENTIALS_UNAVAILABLE").hasNoCause();
    }

    @Test
    void missingAndMalformedKeysHaveSanitizedConfigurationErrors() {
        var missing = new DropboxCredentialCipher("");
        assertThat(missing.configured()).isFalse();
        assertThatThrownBy(() -> missing.encrypt(7, "private-refresh-token")).isInstanceOf(IdentityException.class)
                .hasMessage("DROPBOX_CREDENTIALS_NOT_CONFIGURED").hasNoCause();
        for (String value : new String[] {"private-invalid-key", Base64.getEncoder().encodeToString(new byte[31])}) {
            assertThatThrownBy(() -> new DropboxCredentialCipher(value)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("DROPBOX_CREDENTIAL_KEY_BASE64 must encode exactly 32 bytes").hasNoCause();
        }
    }

    @Test
    void retainsTheExistingTokenLengthBoundary() {
        String maximum = "x".repeat(512);
        assertThat(cipher.decrypt(7, cipher.encrypt(7, maximum))).isEqualTo(maximum);
        for (String value : new String[] {null, "", " ", "x".repeat(513)}) {
            assertThatThrownBy(() -> cipher.encrypt(7, value)).isInstanceOf(IdentityException.class)
                    .hasMessage("VALIDATION_ERROR").hasNoCause();
        }
    }
}
