package com.example.taskmanagement.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.identity.api.IdentityOperations.SessionGrant;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.security.application.TokenService;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenLifetimeTest {
    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private final TokenService tokens = new TokenService(
            "dGVzdC1vbmx5LWtleS1mb3ItczA0LW5vdC1mb3ItcHJvZHVjdGlvbg==", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void accessExpiryNeverExtendsTheRefreshSession() throws Exception {
        var issued = tokens.issue(grant(NOW.plusSeconds(30)));
        assertThat(issued.expiresIn()).isEqualTo(30);
        assertThat(SignedJWT.parse(issued.accessToken()).getJWTClaimsSet().getExpirationTime().toInstant())
                .isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void sessionExpiringBetweenRefreshAndIssuanceProducesAuthenticationError() {
        for (Instant expiry : new Instant[] {NOW, NOW.minusSeconds(1)}) {
            assertThatThrownBy(() -> tokens.issue(grant(expiry)))
                    .isInstanceOf(IdentityException.class).hasMessage("INVALID_SESSION");
        }
    }

    @Test
    void lastFractionOfSessionCannotIssueAnAlreadyExpiredJwt() {
        var nearExpiry = new TokenService("dGVzdC1vbmx5LWtleS1mb3ItczA0LW5vdC1mb3ItcHJvZHVjdGlvbg==",
                Clock.fixed(NOW.plusMillis(900), ZoneOffset.UTC));
        assertThatThrownBy(() -> nearExpiry.issue(grant(NOW.plusMillis(950))))
                .isInstanceOf(IdentityException.class).hasMessage("INVALID_SESSION");
    }

    private static SessionGrant grant(Instant expiresAt) {
        return new SessionGrant(new UserView(1, "user", "user@example.com", "First", "Last", UserRole.USER, 0),
                UUID.randomUUID(), 0, "test-refresh-token", expiresAt);
    }
}
