package com.example.taskmanagement.security;

import com.example.taskmanagement.dto.LoginRequest;
import com.example.taskmanagement.dto.TokenResponse;
import com.example.taskmanagement.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JwtService {
    static final String ISSUER = "task-management-api";
    private static final int MIN_SIGNING_KEY_BYTES = 32;
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String dummyHash;

    public JwtService(UserRepository users, PasswordEncoder passwords,
            JwtEncoder encoder, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = passwords.encode("invalid-login-timing-placeholder");
    }

    public static SecretKeySpec signingKey(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length < MIN_SIGNING_KEY_BYTES) {
                throw new IllegalArgumentException();
            }
            return new SecretKeySpec(decoded, "HmacSHA256");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT key must be Base64 containing at least 32 random bytes");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        if (PasswordPolicy.exceedsBcryptLimit(request.password())) {
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
        var claims = JwtClaimsSet.builder().issuer(ISSUER)
                .subject(found.get().getId().toString()).issuedAt(now).expiresAt(now.plus(ACCESS_TOKEN_LIFETIME))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", ACCESS_TOKEN_LIFETIME.toSeconds());
    }
}
