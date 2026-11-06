package com.example.taskmanagement.identity.application;

import com.example.taskmanagement.identity.api.IdentityException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated credential envelope with a deployment key and an owning-user context. */
public final class DropboxCredentialCipher {
    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public DropboxCredentialCipher(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            key = null;
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedKey.strip());
            if (bytes.length != 32) {
                throw new IllegalArgumentException();
            }
            key = new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("DROPBOX_CREDENTIAL_KEY_BASE64 must encode exactly 32 bytes");
        }
    }

    public boolean configured() {
        return key != null;
    }

    public String encrypt(long userId, String value) {
        requireKey();
        if (value == null || value.isBlank() || value.length() > 512) {
            throw new IdentityException(IdentityException.Reason.VALIDATION_ERROR);
        }
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, userId, nonce)
                    .doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw unavailable();
        }
    }

    public String decrypt(long userId, String value) {
        requireKey();
        if (value == null || !value.startsWith(PREFIX) || value.length() > 4096) {
            throw unavailable();
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length < NONCE_BYTES + 16) {
                throw unavailable();
            }
            byte[] nonce = Arrays.copyOf(payload, NONCE_BYTES);
            String plain = new String(cipher(Cipher.DECRYPT_MODE, userId, nonce)
                    .doFinal(payload, NONCE_BYTES, payload.length - NONCE_BYTES), StandardCharsets.UTF_8);
            if (plain.isBlank() || plain.length() > 512) {
                throw unavailable();
            }
            return plain;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private Cipher cipher(int mode, long userId, byte[] nonce) throws GeneralSecurityException {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("projectflow:dropbox:" + userId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private void requireKey() {
        if (!configured()) {
            throw new IdentityException(IdentityException.Reason.DROPBOX_CREDENTIALS_NOT_CONFIGURED);
        }
    }

    private static IdentityException unavailable() {
        return new IdentityException(IdentityException.Reason.DROPBOX_CREDENTIALS_UNAVAILABLE);
    }
}
