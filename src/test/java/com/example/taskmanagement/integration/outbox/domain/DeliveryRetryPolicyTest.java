package com.example.taskmanagement.integration.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryRetryPolicyTest {
    private final DeliveryRetryPolicy policy = new DeliveryRetryPolicy();
    private final Instant failure = Instant.parse("2026-11-06T10:00:00Z");

    @Test
    void usesApprovedDelaysFromTheFailureTimeAndCapsFurtherRetries() {
        assertThat(policy.nextAttempt(failure, 1)).isEqualTo(failure.plusSeconds(60));
        assertThat(policy.nextAttempt(failure, 2)).isEqualTo(failure.plusSeconds(300));
        assertThat(policy.nextAttempt(failure, 3)).isEqualTo(failure.plusSeconds(900));
        assertThat(policy.nextAttempt(failure, 4)).isEqualTo(failure.plusSeconds(3600));
        assertThat(policy.nextAttempt(failure, Integer.MAX_VALUE)).isEqualTo(failure.plusSeconds(3600));
    }

    @Test
    void rejectsCountsThatDoNotRepresentAFailure() {
        assertThatThrownBy(() -> policy.nextAttempt(failure, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.nextAttempt(failure, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
