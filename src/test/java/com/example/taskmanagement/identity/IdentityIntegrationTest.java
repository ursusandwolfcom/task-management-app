package com.example.taskmanagement.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.support.ConcurrentCalls;
import com.example.taskmanagement.support.DatabaseFixtures;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class IdentityIntegrationTest {
    private static final String PASSWORD = "valid-passphrase-2026";
    private static final String TEST_KEY = "dGVzdC1vbmx5LWtleS1mb3ItczA0LW5vdC1mb3ItcHJvZHVjdGlvbg==";
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private IdentityOperations identity;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private NimbusJwtDecoder decoder;

    @BeforeEach
    void clearIdentityFixtures() {
        DatabaseFixtures.clearIdentity(jdbc);
    }

    @Test
    void registerNormalizesCreatesUserAndNeverReturnsCredentials() throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", " Alice ", "email", " Alice@Example.com ",
                        "password", PASSWORD, "firstName", " Alice ", "lastName", " Doe ", "role", "ADMIN"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.firstName").value("Alice")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password", "hash", PASSWORD);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        String hash = jdbc.queryForObject("select password_hash from task_management.users", String.class);
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void loginAcceptsBothIdentifiersAndWrongCredentialsHaveSameSafeError() throws Exception {
        register("alice");
        for (String id : List.of(" ALICE ", " ALICE@EXAMPLE.COM ")) {
            var tokens = login(id);
            assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
            assertThat(tokens.path("expiresIn").asInt()).isEqualTo(900);
            mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
        }
        for (String id : List.of("alice", "unknown")) {
            var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("identifier", id, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(id, PASSWORD, "wrong-password");
        }
    }

    @Test
    void duplicateUsernameOrEmailReturnsConflict() throws Exception {
        register("alice");
        for (var command : List.of(command("ALICE", "other@example.com"), command("other", "ALICE@EXAMPLE.COM"))) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(command))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_IDENTITY"));
        }
        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    void invalidInputsNeverCreateAnAccount() throws Exception {
        for (var command : List.of(command("ab", "a@example.com"), command("user@name", "a@example.com"),
                command("valid", "invalid"), new RegisterUser("valid", "a@example.com", "short", "A", "B"),
                new RegisterUser("valid", "a@example.com", PASSWORD, " ", "B"),
                new RegisterUser("valid", "a@example.com", PASSWORD, "x".repeat(101), "B"))) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(command))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(userCount()).isZero();
    }

    @Test
    void anonymousApiIsDeniedAndOtherProfilesCannotBeAddressed() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        var alice = register("alice");
        var bob = register("bob");
        var tokens = login("alice");
        mvc.perform(get("/api/users/" + bob.path("id").asLong()).header("Authorization", bearer(tokens)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/users/me").header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "firstName", "Changed", "version", alice.path("version").asLong(),
                        "id", bob.path("id").asLong()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(alice.path("id").asLong()));
        assertThat(identity.profile(bob.path("id").asLong()).firstName()).isEqualTo("First");
    }

    @Test
    void profileUpdatesUsernameAndEmailAndRequiresCurrentVersion() throws Exception {
        var alice = register("alice");
        var tokens = login("alice");
        long initial = alice.path("version").asLong();
        var profile = json.readTree(mvc.perform(put("/api/users/me").header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "username", " RENAMED ", "email", " NEW@EXAMPLE.COM ", "firstName", "New",
                        "lastName", "Name", "version", initial)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("renamed"))
                .andReturn().getResponse().getContentAsString());
        assertThat(profile.path("version").asLong()).isGreaterThan(initial);
        mvc.perform(patch("/api/users/me").header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Stale\",\"version\":" + initial + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(patch("/api/users/me").header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("firstName", " ",
                        "version", profile.path("version").asLong())))).andExpect(status().isBadRequest());
        login("new@example.com");
        login("renamed");
    }

    @Test
    void profileDuplicateConflictPreservesBothUsers() throws Exception {
        var alice = register("alice");
        register("bob");
        var tokens = login("alice");
        mvc.perform(patch("/api/users/me").header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "email", " BOB@EXAMPLE.COM ", "version", alice.path("version").asLong()))))
                .andExpect(status().isConflict());
        assertThat(identity.profile(alice.path("id").asLong()).email()).isEqualTo("alice@example.com");
        assertThat(userCount()).isEqualTo(2);
    }

    @Test
    void onlyCurrentDatabaseAdminCanChangeRolesAndDemotionAffectsOldJwt() throws Exception {
        var admin = identity.bootstrapAdmin(command("admin", "admin@example.com"));
        var user = register("alice");
        var userToken = login("alice");
        mvc.perform(put("/api/users/9223372036854775807/role")
                .header("Authorization", bearer(userToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/" + user.path("id").asLong() + "/role")
                .header("Authorization", bearer(userToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\",\"version\":0}"))
                .andExpect(status().isForbidden());
        var adminToken = login("admin");
        mvc.perform(put("/api/users/" + user.path("id").asLong() + "/role")
                .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(put("/api/users/" + admin.id() + "/role").header("Authorization", bearer(userToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\",\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/users/" + user.path("id").asLong() + "/role")
                .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"USER\",\"version\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshRotatesTokenKeepsAbsoluteExpiryAndStoresOnlyHashes() throws Exception {
        register("alice");
        var first = login("alice");
        var second = refresh(first.path("refreshToken").asText(), 200);
        assertThat(second.path("refreshToken").asText()).isNotEqualTo(first.path("refreshToken").asText());
        assertThat(second.path("refreshExpiresAt").asText()).isEqualTo(first.path("refreshExpiresAt").asText());
        refresh(first.path("refreshToken").asText(), 401);
        String hash = jdbc.queryForObject("select token_hash from task_management.identity_sessions", String.class);
        assertThat(hash).hasSize(64).doesNotContain(first.path("refreshToken").asText(),
                second.path("refreshToken").asText());
        refresh(second.path("refreshToken").asText(), 200);
    }

    @Test
    void expiredSessionRejectsAccessAndRefresh() throws Exception {
        register("alice");
        var tokens = login("alice");
        jdbc.update("update task_management.identity_sessions set expires_at = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
        refresh(tokens.path("refreshToken").asText(), 401);
        mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens))).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesOnlyCurrentSessionIncludingItsAccessToken() throws Exception {
        register("alice");
        var one = login("alice");
        var two = login("alice");
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(one))).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(one))).andExpect(status().isUnauthorized());
        refresh(one.path("refreshToken").asText(), 401);
        mvc.perform(get("/api/users/me").header("Authorization", bearer(two))).andExpect(status().isOk());
        refresh(two.path("refreshToken").asText(), 200);
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndRevokesEverySession() throws Exception {
        register("alice");
        var one = login("alice");
        var two = login("alice");
        mvc.perform(post("/api/users/me/password").header("Authorization", bearer(one))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "currentPassword", "wrong", "newPassword", "replacement-passphrase"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(one))).andExpect(status().isOk());
        mvc.perform(post("/api/users/me/password").header("Authorization", bearer(one))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "currentPassword", PASSWORD, "newPassword", "replacement-passphrase"))))
                .andExpect(status().isNoContent());
        for (var tokens : List.of(one, two)) {
            mvc.perform(get("/api/users/me").header("Authorization", bearer(tokens)))
                    .andExpect(status().isUnauthorized());
            refresh(tokens.path("refreshToken").asText(), 401);
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier", "alice", "password", "replacement-passphrase"))))
                .andExpect(status().isOk());
    }

    @Test
    void damagedExpiredWrongIssuerAudienceAndKeyTokensAreDenied() throws Exception {
        register("alice");
        var tokens = login("alice");
        var actual = decoder.decode(tokens.path("accessToken").asText());
        var originalClaims = JwtClaimsSet.builder().claims(claims -> claims.putAll(actual.getClaims())).build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(TokenService.signingKey(TEST_KEY)));
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        for (var claims : List.of(
                JwtClaimsSet.from(originalClaims).issuedAt(Instant.now().minusSeconds(60))
                        .notBefore(Instant.now().minusSeconds(60)).expiresAt(Instant.now().minusSeconds(1)).build(),
                JwtClaimsSet.from(originalClaims).issuer("other").build(),
                JwtClaimsSet.from(originalClaims).audience(List.of("other")).build(),
                JwtClaimsSet.from(originalClaims).claims(claims -> claims.remove("aud")).build(),
                JwtClaimsSet.from(originalClaims).claim("sid", "not-a-uuid").build(),
                JwtClaimsSet.from(originalClaims).claim("cv", "not-a-number").build(),
                JwtClaimsSet.from(originalClaims).claim("cv", 0.5).build(),
                JwtClaimsSet.from(originalClaims).notBefore(Instant.now().plusSeconds(60)).build(),
                JwtClaimsSet.from(originalClaims).claim("cv", 999L).build())) {
            String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        var foreign = new NimbusJwtEncoder(new ImmutableSecret<>(TokenService.signingKey(
                "YW5vdGhlci10ZXN0LWtleS10aGF0LWlzLW5vdC1wcm9kdWN0aW9u")));
        String foreignToken = foreign.encode(JwtEncoderParameters.from(header,
                JwtClaimsSet.from(originalClaims).build())).getTokenValue();
        for (String token : List.of(foreignToken, "broken", "eyJhbGciOiJub25lIn0.e30.")) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void lastAdminCannotBeDemotedButRoleNoOpIsAllowed() throws Exception {
        var admin = identity.bootstrapAdmin(command("admin", "admin@example.com"));
        var tokens = login("admin");
        mvc.perform(put("/api/users/" + admin.id() + "/role").header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/users/" + admin.id() + "/role").header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\",\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        assertThat(identity.profile(admin.id()).role()).isEqualTo(UserRole.ADMIN);
        assertThat(identity.profile(admin.id()).version()).isEqualTo(admin.version());
    }

    @Test
    void concurrentAdminDemotionsPreserveOneAdmin() throws Exception {
        var first = identity.bootstrapAdmin(command("admin_one", "one@example.com"));
        var second = identity.register(command("admin_two", "two@example.com"));
        var promoted = identity.changeRole(first.id(), second.id(), UserRole.ADMIN, second.version());
        var barrier = new CyclicBarrier(2);
        var results = race(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            return identity.changeRole(first.id(), first.id(), UserRole.USER, first.version());
        }, () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return identity.changeRole(promoted.id(), promoted.id(), UserRole.USER, promoted.version());
        });
        assertThat(results.stream().filter(IdentityException.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(IdentityException.class::isInstance)
                .map(IdentityException.class::cast).map(Throwable::getMessage)).containsExactly("LAST_ADMIN");
        assertThat(jdbc.queryForObject("select count(*) from task_management.users where role = 'ADMIN'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentRegistrationCreatesExactlyOneUsernameOrEmail() throws Exception {
        for (boolean sameUsername : List.of(true, false)) {
            jdbc.update("delete from task_management.users");
            var barrier = new CyclicBarrier(2);
            List<Object> results = race(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return identity.register(command("shared", "first@example.com"));
            }, () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return identity.register(command(sameUsername ? "SHARED" : "second",
                        sameUsername ? "second@example.com" : "FIRST@EXAMPLE.COM"));
            });
            assertThat(results.stream().filter(DataIntegrityViolationException.class::isInstance).count()).isEqualTo(1);
            assertThat(userCount()).isEqualTo(1);
        }
    }

    @Test
    void concurrentRefreshConsumesOneTokenExactlyOnce() throws Exception {
        register("alice");
        String token = login("alice").path("refreshToken").asText();
        var barrier = new CyclicBarrier(2);
        Callable<Object> attempt = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return identity.refresh(token);
        };
        var results = race(attempt, attempt);
        assertThat(results.stream().filter(IdentityOperations.SessionGrant.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(IdentityException.class::isInstance).count()).isEqualTo(1);
    }

    @Test
    void concurrentBootstrapCreatesExactlyOneAdminAndRepeatFails() throws Exception {
        var barrier = new CyclicBarrier(2);
        var results = race(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            return identity.bootstrapAdmin(command("admin_one", "one@example.com"));
        }, () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return identity.bootstrapAdmin(command("admin_two", "two@example.com"));
        });
        assertThat(results.stream().filter(IdentityException.class::isInstance).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from task_management.users where role = 'ADMIN'",
                Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> identity.bootstrapAdmin(command("another", "another@example.com")))
                .isInstanceOf(IdentityException.class).hasMessage("ADMIN_ALREADY_EXISTS");
    }

    private JsonNode register(String username) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(command(username, username + "@example.com"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode login(String identifier) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier", identifier, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode refresh(String token, int expectedStatus) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }

    private static RegisterUser command(String username, String email) {
        return new RegisterUser(username, email, PASSWORD, "First", "Last");
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.path("accessToken").asText();
    }

    private int userCount() {
        return jdbc.queryForObject("select count(*) from task_management.users", Integer.class);
    }

    private static List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        return ConcurrentCalls.race(30, List.of(IdentityException.class, DataIntegrityViolationException.class),
                first, second);
    }
}
