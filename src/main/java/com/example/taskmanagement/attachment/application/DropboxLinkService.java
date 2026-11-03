package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.DropboxLinkOperations;
import com.example.taskmanagement.attachment.api.DropboxOAuthClient;
import com.example.taskmanagement.identity.api.DropboxAccountOperations;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class DropboxLinkService implements DropboxLinkOperations {
    private static final long STATE_TTL_SECONDS = 900;
    private final DropboxAccountOperations accounts;
    private final DropboxOAuthClient oauth;
    private final Clock clock;
    private final String redirectUri;
    private final String stateSecret;

    public DropboxLinkService(DropboxAccountOperations accounts, DropboxOAuthClient oauth, Clock clock,
            @Value("${dropbox.oauth-redirect-uri:}") String redirectUri,
            @Value("${dropbox.app-secret:}") String appSecret,
            @Value("${identity.jwt-key-base64:}") String jwtKey) {
        this.accounts = accounts;
        this.oauth = oauth;
        this.clock = clock;
        this.redirectUri = blank(redirectUri);
        String secret = blank(appSecret);
        this.stateSecret = secret.isEmpty() ? blank(jwtKey) : secret;
    }

    @Override
    @Transactional(readOnly = true)
    public DropboxChannelStatus status(long userId) {
        var linked = accounts.status(userId);
        return new DropboxChannelStatus(oauth.configured() && !redirectUri.isEmpty(), linked.linked(),
                linked.accountId(), linked.linkedAt());
    }

    @Override
    public AuthorizeResponse beginAuthorize(long userId) {
        requireOAuth();
        accounts.status(userId);
        String state = signState(userId, clock.instant().getEpochSecond() + STATE_TTL_SECONDS);
        return new AuthorizeResponse(oauth.authorizeUrl(redirectUri, state), redirectUri, state);
    }

    @Override
    public DropboxChannelStatus completeAuthorize(long userId, String code, String state) {
        requireOAuth();
        verifyState(userId, state);
        var tokens = oauth.exchangeCode(redirectUri, code);
        accounts.link(userId, tokens.refreshToken(), tokens.accountId());
        return status(userId);
    }

    @Override
    public DropboxChannelStatus unlink(long userId) {
        accounts.unlink(userId);
        return status(userId);
    }

    private void requireOAuth() {
        if (!oauth.configured() || redirectUri.isEmpty() || stateSecret.isEmpty()) {
            throw new AttachmentException(AttachmentException.Reason.OAUTH_NOT_CONFIGURED);
        }
    }

    private String signState(long userId, long expiresAtEpochSeconds) {
        String payload = userId + ":" + expiresAtEpochSeconds;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + ":" + hmac(payload)).getBytes(StandardCharsets.UTF_8));
    }

    private void verifyState(long userId, String state) {
        if (state == null || state.isBlank()) {
            throw new AttachmentException(AttachmentException.Reason.OAUTH_STATE_INVALID);
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state.strip()), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 3);
            if (parts.length != 3) {
                throw new AttachmentException(AttachmentException.Reason.OAUTH_STATE_INVALID);
            }
            long stateUser = Long.parseLong(parts[0]);
            long expires = Long.parseLong(parts[1]);
            String expected = hmac(parts[0] + ":" + parts[1]);
            if (stateUser != userId || expires < clock.instant().getEpochSecond() || !expected.equals(parts[2])) {
                throw new AttachmentException(AttachmentException.Reason.OAUTH_STATE_INVALID);
            }
        } catch (AttachmentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AttachmentException(AttachmentException.Reason.OAUTH_STATE_INVALID);
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AttachmentException(AttachmentException.Reason.STORAGE_ERROR);
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value.strip();
    }
}
