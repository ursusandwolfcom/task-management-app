package com.example.taskmanagement.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.security.web.BrowserSessionCookies;
import com.example.taskmanagement.support.ConcurrentCalls;
import com.example.taskmanagement.support.DatabaseFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class BrowserSessionIntegrationTest {
    private static final String PASSWORD = "valid-passphrase-2026";
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private IdentityOperations identity;
    @Autowired
    private JdbcTemplate jdbc;

    private record Csrf(Cookie cookie, String header, String token) {
    }

    @BeforeEach
    void clearFixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
        identity.register(new RegisterUser("alice", "alice@example.com", PASSWORD, "Alice", "Doe"));
    }

    @Test
    void loginAndSessionCommandsRequireCsrfEvenWithAuthorizationHeader() throws Exception {
        for (String path : List.of("/api/session/login", "/api/session/refresh", "/api/session/logout")) {
            mvc.perform(post(path).header("Authorization", "Bearer cannot-bypass-csrf")
                    .contentType(MediaType.APPLICATION_JSON).content(credentials()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }
        assertThat(jdbc.queryForObject("select count(*) from task_management.identity_sessions", Integer.class))
                .isZero();
    }

    @Test
    void loginPersistsOnlyHostSecureHttpOnlyCookiesAndReturnsSafeProfile() throws Exception {
        var login = login();
        var body = json.readTree(login.getResponse().getContentAsString());
        assertThat(body.path("user").path("username").asText()).isEqualTo("alice");
        assertThat(login.getResponse().getContentAsString()).doesNotContain("Token", PASSWORD);
        assertThat(login.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        for (String name : List.of(BrowserSessionCookies.ACCESS, BrowserSessionCookies.REFRESH)) {
            var cookie = login.getResponse().getCookie(name);
            assertThat(cookie).isNotNull();
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSecure()).isTrue();
            assertThat(cookie.getDomain()).isNull();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getMaxAge()).isPositive();
            assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        }
        assertThat(login.getResponse().getCookie(BrowserSessionCookies.ACCESS).getMaxAge()).isLessThanOrEqualTo(900);
        assertThat(login.getResponse().getCookie(BrowserSessionCookies.CSRF).getMaxAge()).isZero();
        mvc.perform(get("/api/users/me").cookie(access(login)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void cookieProfileWriteRequiresCsrfAndDoesNotChangeDataOnRejection() throws Exception {
        var login = login();
        String change = json.writeValueAsString(Map.of("username", "alice", "email", "alice@example.com",
                "firstName", "Changed", "lastName", "Doe", "version", 0));
        mvc.perform(put("/api/users/me").cookie(access(login)).contentType(MediaType.APPLICATION_JSON).content(change))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me").cookie(access(login)))
                .andExpect(jsonPath("$.firstName").value("Alice"));
        var csrf = csrf();
        mvc.perform(put("/api/users/me").cookie(access(login), csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(change))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Changed"))
                .andExpect(result -> assertThat(result.getResponse().getCookie(BrowserSessionCookies.CSRF)).isNull());
    }

    @Test
    void explicitBearerKeepsExistingContractAndNeverFallsBackToCookie() throws Exception {
        var login = login();
        String token = access(login).getValue();
        mvc.perform(post("/api/session/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(get("/api/users/me").cookie(access(login)).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").cookie(access(login)).header("Authorization", "Basic invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").cookie(access(login)).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").cookie(access(login))).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWorksWithExpiredAccessRotatesOnceAndPreservesDeadline() throws Exception {
        var login = login();
        var grant = identity.login("alice", PASSWORD);
        var oldTokens = new TokenService(
                "dGVzdC1vbmx5LWtleS1mb3ItczA0LW5vdC1mb3ItcHJvZHVjdGlvbg==",
                Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC)).issue(grant);
        var expired = new Cookie(BrowserSessionCookies.ACCESS, oldTokens.accessToken());
        mvc.perform(get("/api/users/me").cookie(expired)).andExpect(status().isUnauthorized());
        var csrf = csrf();
        var refreshed = mvc.perform(post("/api/session/refresh").cookie(expired, refresh(login), csrf.cookie())
                .header(csrf.header(), csrf.token())).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(refreshed.getResponse().getContentAsString()).path("expiresAt").asText())
                .isEqualTo(json.readTree(login.getResponse().getContentAsString()).path("expiresAt").asText());
        assertThat(refresh(refreshed).getValue()).isNotEqualTo(refresh(login).getValue());
        var rejected = mvc.perform(post("/api/session/refresh").cookie(refresh(login), csrf.cookie())
                .header(csrf.header(), csrf.token())).andExpect(status().isUnauthorized()).andReturn();
        assertThat(rejected.getResponse().getHeaders("Set-Cookie")).isEmpty();
        mvc.perform(get("/api/users/me").cookie(access(refreshed))).andExpect(status().isOk());
    }

    @Test
    void logoutWorksWithoutValidAccessAndRevokesBothCredentials() throws Exception {
        var login = login();
        var csrf = csrf();
        var logout = mvc.perform(post("/api/session/logout").cookie(refresh(login), csrf.cookie())
                .header(csrf.header(), csrf.token())).andExpect(status().isNoContent()).andReturn();
        assertThat(logout.getResponse().getCookie(BrowserSessionCookies.ACCESS).getMaxAge()).isZero();
        assertThat(logout.getResponse().getCookie(BrowserSessionCookies.REFRESH).getMaxAge()).isZero();
        mvc.perform(get("/api/users/me").cookie(access(login))).andExpect(status().isUnauthorized());
        var fresh = csrf();
        mvc.perform(post("/api/session/refresh").cookie(refresh(login), fresh.cookie())
                .header(fresh.header(), fresh.token())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/session/logout").cookie(refresh(login), fresh.cookie())
                .header(fresh.header(), fresh.token())).andExpect(status().isNoContent());
    }

    @Test
    void passwordChangeRevokesCookieAccessAndRefresh() throws Exception {
        var login = login();
        var csrf = csrf();
        mvc.perform(post("/api/users/me/password").cookie(access(login), csrf.cookie())
                .header(csrf.header(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD,
                        "newPassword", "new-valid-passphrase-2026")))).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").cookie(access(login))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/session/refresh").cookie(refresh(login), csrf.cookie())
                .header(csrf.header(), csrf.token())).andExpect(status().isUnauthorized());
    }

    @Test
    void competingRefreshHasOneWinnerAndLoserDoesNotEraseCookies() throws Exception {
        var login = login();
        var csrf = csrf();
        var barrier = new CyclicBarrier(2);
        Callable<Object> call = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return mvc.perform(post("/api/session/refresh").cookie(refresh(login), csrf.cookie())
                    .header(csrf.header(), csrf.token())).andReturn();
        };
        var results = ConcurrentCalls.race(15, List.of(), call, call).stream().map(MvcResult.class::cast).toList();
        assertThat(results.stream().map(result -> result.getResponse().getStatus()).sorted().toList())
                .containsExactly(200, 401);
        var loser = results.stream().filter(result -> result.getResponse().getStatus() == 401)
                .findFirst().orElseThrow();
        assertThat(loser.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void cookieProjectsUseDatabaseAclAndExpectedVersion() throws Exception {
        var alice = login();
        var csrf = csrf();
        var created = mvc.perform(post("/api/projects").cookie(access(alice), csrf.cookie())
                .header(csrf.header(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Private", "description", "",
                        "startDate", "2026-10-13", "endDate", "2026-10-14", "timezone", "UTC"))))
                .andExpect(status().isCreated()).andReturn();
        var project = json.readTree(created.getResponse().getContentAsString());
        long id = project.path("id").asLong();
        identity.register(new RegisterUser("bob", "bob@example.com", PASSWORD, "Bob", "Doe"));
        var bobCsrf = csrf();
        var bob = mvc.perform(post("/api/session/login").cookie(bobCsrf.cookie())
                .header(bobCsrf.header(), bobCsrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier", "bob", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/projects/" + id).cookie(access(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects").cookie(access(bob))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(put("/api/projects/" + id + "/owner").cookie(access(alice), csrf.cookie())
                .header(csrf.header(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("userId", project.path("ownerId").asLong(),
                        "version", project.path("version").asLong() + 1))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void expiredSessionRejectsAccessAndRefreshButDoesNotBlockCsrfBootstrap() throws Exception {
        var login = login();
        jdbc.update("update task_management.identity_sessions set expires_at = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
        mvc.perform(get("/api/users/me").cookie(access(login))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/session/csrf").cookie(access(login))).andExpect(status().isOk());
        var csrf = csrf();
        mvc.perform(post("/api/session/refresh").cookie(refresh(login), csrf.cookie())
                .header(csrf.header(), csrf.token())).andExpect(status().isUnauthorized());
    }

    @Test
    void ambiguousCookiesAndMissingRefreshNeverAuthenticate() throws Exception {
        var login = login();
        mvc.perform(get("/api/users/me").cookie(access(login), new Cookie(BrowserSessionCookies.ACCESS, "invalid")))
                .andExpect(status().isUnauthorized());
        var csrf = csrf();
        for (Cookie[] cookies : List.of(new Cookie[] {csrf.cookie()},
                new Cookie[] {csrf.cookie(), new Cookie(BrowserSessionCookies.REFRESH, "malformed")},
                new Cookie[] {csrf.cookie(), refresh(login), refresh(login)})) {
            mvc.perform(post("/api/session/refresh").cookie(cookies).header(csrf.header(), csrf.token()))
                    .andExpect(status().isUnauthorized());
        }
    }

    private Csrf csrf() throws Exception {
        var result = mvc.perform(get("/api/session/csrf")).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        var cookie = result.getResponse().getCookie(BrowserSessionCookies.CSRF);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        return new Csrf(cookie, body.path("headerName").asText(), body.path("token").asText());
    }

    private MvcResult login() throws Exception {
        var csrf = csrf();
        return mvc.perform(post("/api/session/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(credentials())).andExpect(status().isOk()).andReturn();
    }

    private String credentials() throws Exception {
        return json.writeValueAsString(Map.of("identifier", "alice", "password", PASSWORD));
    }

    private static Cookie access(MvcResult result) {
        return result.getResponse().getCookie(BrowserSessionCookies.ACCESS);
    }

    private static Cookie refresh(MvcResult result) {
        return result.getResponse().getCookie(BrowserSessionCookies.REFRESH);
    }
}
