package com.example.taskmanagement.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.identity.persistence.SessionEntity;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityValueTest {
    @Test
    void normalizationIsDeterministicAndPreservesPasswordWhitespace() {
        var input = new RegisterUser(" USER.I ", " I@EXAMPLE.COM ", " secret with spaces ", " First ", " Last ");
        assertThat(input.username()).isEqualTo("user.i");
        assertThat(input.email()).isEqualTo("i@example.com");
        assertThat(input.firstName()).isEqualTo("First");
        assertThat(input.lastName()).isEqualTo("Last");
        assertThat(input.password()).isEqualTo(" secret with spaces ");
        assertThat(input.toString()).doesNotContain(input.password(), input.email());
    }

    @Test
    void constraintsRejectBoundaryViolationsAndAcceptMaximumLengths() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var valid = new RegisterUser("u".repeat(50), "valid@example.com", "p".repeat(128),
                    "F".repeat(100), "L".repeat(100));
            assertThat(validator.validate(valid)).isEmpty();
            for (var invalid : List.of(new RegisterUser(null, null, null, null, null),
                    new RegisterUser("u".repeat(51), "valid@example.com", "p".repeat(128), "F", "L"),
                    new RegisterUser("user", "valid@example.com", "p".repeat(129), "F", "L"),
                    new RegisterUser("user", "valid@example.com", "p".repeat(11), "F", "L"))) {
                assertThat(validator.validate(invalid)).isNotEmpty();
            }
        }
    }

    @Test
    void sessionExpiryRotationAndRevocationRespectExactBoundaries() {
        Instant expiry = Instant.parse("2026-10-20T00:00:00Z");
        var session = new SessionEntity(UUID.randomUUID(), 1, "old-hash", expiry);
        assertThat(session.accepts("old-hash", expiry.minusMillis(1))).isTrue();
        assertThat(session.active(expiry)).isFalse();
        assertThat(session.active(expiry.plusMillis(1))).isFalse();
        session.rotate("new-hash");
        assertThat(session.accepts("old-hash", expiry.minusSeconds(1))).isFalse();
        assertThat(session.accepts("new-hash", expiry.minusSeconds(1))).isTrue();
        session.revoke();
        assertThat(session.active(expiry.minusSeconds(1))).isFalse();
        assertThat(session.expiresAt()).isEqualTo(expiry);
    }

    @Test
    void publicProfileHasOnlySafeDocumentedComponents() {
        assertThat(Arrays.stream(UserView.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("id", "username", "email", "firstName", "lastName", "role", "version");
    }
}
