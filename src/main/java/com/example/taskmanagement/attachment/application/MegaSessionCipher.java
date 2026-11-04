package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.MegaException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-256-GCM with a separate deployment key and user ID as authenticated context. */
@Component
public class MegaSessionCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public MegaSessionCipher(@Value("${mega.session-key-base64:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            key = null;
        } else {
            byte[] bytes = Base64.getDecoder().decode(encodedKey.strip());
            if (bytes.length != 32) {
                throw new IllegalArgumentException("MEGA_SESSION_KEY_BASE64 must encode exactly 32 bytes");
            }
            key = new SecretKeySpec(bytes, "AES");
        }
    }

    public boolean configured() {
        return key != null;
    }

    public String encrypt(long userId, String value) {
        requireKey();
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, userId, nonce)
                    .doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException exception) {
            throw new MegaException(MegaException.Reason.MEGA_STORAGE_ERROR);
        }
    }

    public String decrypt(long userId, String value) {
        requireKey();
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length < 28) {
                throw new IllegalArgumentException();
            }
            byte[] nonce = java.util.Arrays.copyOf(payload, 12);
            return new String(cipher(Cipher.DECRYPT_MODE, userId, nonce)
                    .doFinal(payload, 12, payload.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new MegaException(MegaException.Reason.MEGA_ACCOUNT_UNAVAILABLE);
        }
    }

    private Cipher cipher(int mode, long userId, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("projectflow:mega:" + userId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private void requireKey() {
        if (!configured()) {
            throw new MegaException(MegaException.Reason.MEGA_NOT_CONFIGURED);
        }
    }
}
