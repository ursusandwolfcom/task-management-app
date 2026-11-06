package com.example.taskmanagement.security;

import com.example.taskmanagement.dto.LoginRequest;
import com.example.taskmanagement.dto.TokenResponse;
import com.example.taskmanagement.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JwtService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String dummyHash;

    public JwtService(UserRepository users, PasswordEncoder passwords,
            @Value("${jwt.key-base64}") String key, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(key)));
        this.clock = clock;
        this.dummyHash = passwords.encode("invalid-login-timing-placeholder");
    }

    public static SecretKeySpec signingKey(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length < 32) {
                throw new IllegalArgumentException();
            }
            return new SecretKeySpec(decoded, "HmacSHA256");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT key must be Base64 containing at least 32 random bytes");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String identifier = request.identifier().trim().toLowerCase(Locale.ROOT);
        var found = identifier.contains("@") ? users.findByEmailIgnoreCase(identifier)
                : users.findByUsernameIgnoreCase(identifier);
        String hash = found.isPresent() ? found.get().getPasswordHash() : dummyHash;
        boolean matches = passwords.matches(request.password(), hash);
        if (found.isEmpty() || !matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer("task-management-api")
                .subject(found.get().getId().toString()).issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(15)))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", 900);
    }
}
