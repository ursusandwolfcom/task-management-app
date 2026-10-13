package com.example.taskmanagement.security.application;

import com.example.taskmanagement.identity.api.IdentityOperations.SessionGrant;
import com.example.taskmanagement.identity.api.IdentityException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    public record Tokens(String accessToken, String tokenType, long expiresIn,
            String refreshToken, Instant refreshExpiresAt) {
        @Override
        public String toString() {
            return "Tokens[credentials redacted]";
        }
    }

    private final JwtEncoder encoder;
    private final Clock clock;

    public TokenService(@Value("${identity.jwt-key-base64:}") String key, Clock clock) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(key)));
        this.clock = clock;
    }

    public static SecretKeySpec signingKey(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length < 32) {
                throw new IllegalArgumentException();
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT signing key must be valid Base64 with at least 32 random bytes.");
        }
    }

    public Tokens issue(SessionGrant grant) {
        Instant now = clock.instant();
        // JWT NumericDate is encoded in whole seconds; reject a grant that cannot yield a live JWT.
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Instant sessionExpires = grant.refreshExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        Instant expires = issuedAt.plus(Duration.ofMinutes(15));
        if (expires.isAfter(sessionExpires)) {
            expires = sessionExpires;
        }
        if (!now.isBefore(expires)) {
            throw new IdentityException(IdentityException.Reason.INVALID_SESSION);
        }
        var claims = JwtClaimsSet.builder().issuer("projectflow").audience(java.util.List.of("projectflow-api"))
                .subject(Long.toString(grant.user().id())).issuedAt(issuedAt).notBefore(issuedAt).expiresAt(expires)
                .claim("sid", grant.sessionId().toString()).claim("cv", grant.credentialVersion()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Tokens(token, "Bearer", Duration.between(issuedAt, expires).toSeconds(),
                grant.refreshToken(), grant.refreshExpiresAt());
    }
}
