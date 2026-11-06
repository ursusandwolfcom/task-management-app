package com.example.taskmanagement.security;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    public static final int MIN_CHARACTERS = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
