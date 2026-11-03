package com.example.taskmanagement.attachment.api;

import java.time.Instant;

public interface DropboxLinkOperations {
    record DropboxChannelStatus(boolean oauthConfigured, boolean linked, String accountId, Instant linkedAt) {
    }

    record AuthorizeResponse(String authorizeUrl, String redirectUri, String state) {
    }

    DropboxChannelStatus status(long userId);

    AuthorizeResponse beginAuthorize(long userId);

    DropboxChannelStatus completeAuthorize(long userId, String code, String state);

    DropboxChannelStatus unlink(long userId);
}
