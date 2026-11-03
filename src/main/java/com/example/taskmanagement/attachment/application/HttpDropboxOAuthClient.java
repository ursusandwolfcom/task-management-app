package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.DropboxOAuthClient;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class HttpDropboxOAuthClient implements DropboxOAuthClient {
    private final String appKey;
    private final String appSecret;
    private final HttpClient http;
    private final ObjectMapper json;

    public HttpDropboxOAuthClient(String appKey, String appSecret, ObjectMapper json) {
        this.appKey = blank(appKey);
        this.appSecret = blank(appSecret);
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public boolean configured() {
        return !appKey.isEmpty() && !appSecret.isEmpty();
    }

    @Override
    public String authorizeUrl(String redirectUri, String state) {
        requireConfigured();
        return "https://www.dropbox.com/oauth2/authorize?client_id=" + encode(appKey)
                + "&response_type=code&token_access_type=offline&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state);
    }

    @Override
    public TokenBundle exchangeCode(String redirectUri, String code) {
        requireConfigured();
        if (code == null || code.isBlank()) {
            throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
        }
        try {
            String form = "code=" + encode(code.strip())
                    + "&grant_type=authorization_code"
                    + "&redirect_uri=" + encode(redirectUri)
                    + "&client_id=" + encode(appKey)
                    + "&client_secret=" + encode(appSecret);
            HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create("https://api.dropboxapi.com/oauth2/token"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> tokenResponse = http.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (tokenResponse.statusCode() / 100 != 2) {
                throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
            }
            JsonNode tokenJson = json.readTree(tokenResponse.body());
            String refresh = text(tokenJson, "refresh_token");
            String access = text(tokenJson, "access_token");
            if (refresh.isEmpty() || access.isEmpty()) {
                throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
            }
            HttpRequest accountRequest = HttpRequest.newBuilder(
                            URI.create("https://api.dropboxapi.com/2/users/get_current_account"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + access)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> accountResponse = http.send(accountRequest, HttpResponse.BodyHandlers.ofString());
            if (accountResponse.statusCode() / 100 != 2) {
                throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
            }
            String accountId = text(json.readTree(accountResponse.body()), "account_id");
            if (accountId.isEmpty()) {
                throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
            }
            return new TokenBundle(refresh, accountId);
        } catch (AttachmentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new AttachmentException(AttachmentException.Reason.OAUTH_NOT_CONFIGURED);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString().strip();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String blank(String value) {
        return value == null ? "" : value.strip();
    }
}
