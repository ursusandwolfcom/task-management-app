package com.example.taskmanagement.security.application;

import com.example.taskmanagement.identity.api.IdentityOperations.SessionGrant;
import com.example.taskmanagement.identity.api.IdentityException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
        if (!now.isBefore(grant.refreshExpiresAt())) {
            throw new IdentityException(IdentityException.Reason.INVALID_SESSION);
        }
        Instant expires = now.plus(Duration.ofMinutes(15));
        if (expires.isAfter(grant.refreshExpiresAt())) {
            expires = grant.refreshExpiresAt();
        }
        var claims = JwtClaimsSet.builder().issuer("projectflow").audience(java.util.List.of("projectflow-api"))
                .subject(Long.toString(grant.user().id())).issuedAt(now).notBefore(now).expiresAt(expires)
                .claim("sid", grant.sessionId().toString()).claim("cv", grant.credentialVersion()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Tokens(token, "Bearer", Duration.between(now, expires).toSeconds(),
                grant.refreshToken(), grant.refreshExpiresAt());
    }
}
