package com.example.taskmanagement.integration.outbox.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Approved retry delays after consecutive failures; successful delivery resets the counter. */
public final class DeliveryRetryPolicy {
    private static final List<Duration> DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofMinutes(60));

    public Instant nextAttempt(Instant failedAt, int consecutiveFailures) {
        Objects.requireNonNull(failedAt, "failedAt");
        if (consecutiveFailures < 1) {
            throw new IllegalArgumentException("consecutiveFailures must be positive");
        }
        return failedAt.plus(DELAYS.get(Math.min(consecutiveFailures, DELAYS.size()) - 1));
    }
}
