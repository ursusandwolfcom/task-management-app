package com.example.taskmanagement.attachment.api;

/** Dropbox authorization-code OAuth (offline refresh tokens). */
public interface DropboxOAuthClient {
    record TokenBundle(String refreshToken, String accountId) {
        @Override
        public String toString() {
            return "TokenBundle[redacted]";
        }
    }

    boolean configured();

    String authorizeUrl(String redirectUri, String state);

    TokenBundle exchangeCode(String redirectUri, String code);
}
