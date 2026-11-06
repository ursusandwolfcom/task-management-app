package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.model.Role;
import com.example.taskmanagement.security.JwtService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthenticationTest extends ApiTestSupport {
    @Test
    void registrationLoginAndProfileNeverExposePassword() throws Exception {
        var account = account();
        var profile = json(200, "GET", "/users/me", account.token(), null);
        assertThat(profile.get("username").asText()).isEqualTo(account.name());
        assertThat(profile.has("passwordHash")).isFalse();
        var stored = users.findById(account.id()).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo("password123");
        assertThat(stored.getRole()).isEqualTo(Role.USER);
        json(200, "POST", "/api/auth/login", null,
                Map.of("identifier", account.name().toUpperCase() + "@EXAMPLE.TEST", "password", "password123"));
        json(200, "PUT", "/users/me", account.token(),
                Map.of("email", "new-" + account.name() + "@example.test", "firstName", "New", "lastName", "Name"));
        var patched = json(200, "PATCH", "/users/me", account.token(), Map.of("firstName", "Patched"));
        assertThat(patched.get("firstName").asText()).isEqualTo("Patched");
        assertThat(patched.get("lastName").asText()).isEqualTo("Name");
        assertThat(call("PATCH", "/users/me", account.token(), Map.of("firstName", " ")).getStatus()).isEqualTo(400);
    }

    @Test
    void invalidCredentialsDuplicatesAndEscalationAreRejected() throws Exception {
        var account = account();
        assertThat(call("GET", "/users/me", null, null).getStatus()).isEqualTo(401);
        assertThat(call("GET", "/api/projects", "invalid-token", null).getStatus()).isEqualTo(401);
        assertThat(call("POST", "/api/auth/login", null,
                Map.of("identifier", account.name(), "password", "wrong")).getStatus()).isEqualTo(401);
        assertThat(call("POST", "/api/auth/login", null,
                Map.of("identifier", "does-not-exist", "password", "wrong")).getStatus()).isEqualTo(401);
        var duplicate = Map.of("username", account.name().toUpperCase(), "password", "password123",
                "email", account.name() + "@example.test", "firstName", "A", "lastName", "B");
        assertThat(call("POST", "/api/auth/register", null, duplicate).getStatus()).isEqualTo(409);
        var extraRole = new java.util.HashMap<String, Object>(duplicate);
        extraRole.put("role", "ADMIN");
        assertThat(call("POST", "/api/auth/register", null, extraRole).getStatus()).isEqualTo(400);
        assertThat(call("PUT", "/users/" + account.id() + "/role", account.token(),
                Map.of("role", "ADMIN")).getStatus()).isEqualTo(403);
        assertThat(call("POST", "/api/auth/register", null,
                Map.of("username", "x", "password", "short", "email", "bad",
                        "firstName", " ", "lastName", " ")).getStatus()).isEqualTo(400);
        var unicode = new java.util.HashMap<String, Object>(duplicate);
        unicode.put("password", "я".repeat(50));
        assertThat(call("POST", "/api/auth/register", null, unicode).getStatus()).isEqualTo(400);
        assertThat(call("POST", "/api/auth/login", null,
                Map.of("identifier", account.name(), "password", "я".repeat(50))).getStatus()).isEqualTo(401);
    }

    @Test
    void roleChangesApplyToExistingJwtAndProfilesRemainUnique() throws Exception {
        var admin = account();
        var member = account();
        var entity = users.findById(admin.id()).orElseThrow();
        entity.setRole(Role.ADMIN);
        users.saveAndFlush(entity);
        json(200, "PUT", "/users/" + member.id() + "/role", admin.token(), Map.of("role", "ADMIN"));
        assertThat(json(200, "GET", "/users/me", member.token(), null).get("role").asText()).isEqualTo("ADMIN");
        json(200, "PUT", "/users/" + member.id() + "/role", admin.token(), Map.of("role", "USER"));
        assertThat(call("PUT", "/users/" + admin.id() + "/role", member.token(),
                Map.of("role", "USER")).getStatus()).isEqualTo(403);
        assertThat(call("PATCH", "/users/me", member.token(),
                Map.of("email", admin.name() + "@example.test")).getStatus()).isEqualTo(409);
        assertThat(call("PUT", "/users/999999999/role", admin.token(), Map.of("role", "USER"))
                .getStatus()).isEqualTo(404);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> JwtService.signingKey("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
