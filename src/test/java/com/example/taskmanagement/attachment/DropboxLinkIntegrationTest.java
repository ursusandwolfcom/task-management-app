package com.example.taskmanagement.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.attachment.api.DropboxLinkOperations;
import com.example.taskmanagement.attachment.api.DropboxOAuthClient;
import com.example.taskmanagement.identity.api.DropboxAccountOperations;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class DropboxLinkIntegrationTest {
    private static final String PASSWORD = "dropbox-link-test-passphrase";

    @TestConfiguration
    static class FakeOAuthConfig {
        @Bean
        @Primary
        DropboxOAuthClient fakeDropboxOAuthClient() {
            return new DropboxOAuthClient() {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public String authorizeUrl(String redirectUri, String state) {
                    return "https://www.dropbox.com/oauth2/authorize?state=" + state;
                }

                @Override
                public TokenBundle exchangeCode(String redirectUri, String code) {
                    if (!"good-code".equals(code)) {
                        throw new AttachmentException(AttachmentException.Reason.VALIDATION_ERROR);
                    }
                    return new TokenBundle("refresh-token-1", "dbid:test-account");
                }
            };
        }
    }

    @Autowired private DropboxLinkOperations dropbox;
    @Autowired private DropboxAccountOperations accounts;
    @Autowired private IdentityOperations identity;
    @Autowired private TokenService tokens;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private UserView user;

    @BeforeEach
    void fixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        user = identity.register(new RegisterUser("dropbox_user", "dropbox_user@example.com", PASSWORD, "First",
                "Last"));
    }

    @Test
    void authorizeCallbackAndUnlinkRoundTrip() {
        assertThat(dropbox.status(user.id()).linked()).isFalse();
        var authorize = dropbox.beginAuthorize(user.id());
        assertThat(authorize.authorizeUrl()).contains(authorize.state());
        var linked = dropbox.completeAuthorize(user.id(), "good-code", authorize.state());
        assertThat(linked.linked()).isTrue();
        assertThat(linked.accountId()).isEqualTo("dbid:test-account");
        assertThat(accounts.credential(user.id())).isPresent();
        assertThatThrownBy(() -> dropbox.completeAuthorize(user.id(), "good-code", "bad-state"))
                .isInstanceOf(AttachmentException.class)
                .extracting("reason").isEqualTo(AttachmentException.Reason.OAUTH_STATE_INVALID);
        assertThat(dropbox.unlink(user.id()).linked()).isFalse();
        assertThat(accounts.credential(user.id())).isEmpty();
    }

    @Test
    void httpExposesStatusAuthorizeAndCallback() throws Exception {
        String auth = "Bearer " + tokens.issue(identity.login(user.username(), PASSWORD)).accessToken();
        mvc.perform(get("/api/dropbox").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oauthConfigured").value(true))
                .andExpect(jsonPath("$.linked").value(false));
        String authorizeBody = mvc.perform(post("/api/dropbox/authorize").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String state = authorizeBody.replaceAll("(?s).*\"state\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/dropbox/callback").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"good-code\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.accountId").value("dbid:test-account"));
        mvc.perform(delete("/api/dropbox/link").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false));
    }
}
