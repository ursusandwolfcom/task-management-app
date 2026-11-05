package com.example.taskmanagement.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.DropboxOAuthClient;
import com.example.taskmanagement.identity.api.DropboxAccountOperations;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class DropboxLinkConfigurationTest {
    @Test
    void missingStorageKeyDisablesOAuthBeforeProviderCalls() {
        var accounts = mock(DropboxAccountOperations.class);
        var oauth = mock(DropboxOAuthClient.class);
        when(oauth.configured()).thenReturn(true);
        when(accounts.status(7)).thenReturn(new DropboxAccountOperations.DropboxStatus(false, null, null));
        when(accounts.credentialStorageConfigured()).thenReturn(false);
        var service = new DropboxLinkService(accounts, oauth, Clock.systemUTC(),
                "http://localhost/dropbox/callback", "test-app-secret", "test-jwt-key");
        assertThat(service.status(7).oauthConfigured()).isFalse();
        assertThatThrownBy(() -> service.beginAuthorize(7)).isInstanceOf(AttachmentException.class)
                .hasMessage("OAUTH_NOT_CONFIGURED");
        assertThatThrownBy(() -> service.completeAuthorize(7, "code", "state"))
                .isInstanceOf(AttachmentException.class).hasMessage("OAUTH_NOT_CONFIGURED");
        verify(oauth, never()).authorizeUrl(anyString(), anyString());
        verify(oauth, never()).exchangeCode(anyString(), anyString());
    }
}
