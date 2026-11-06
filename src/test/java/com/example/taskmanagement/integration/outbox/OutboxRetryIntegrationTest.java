package com.example.taskmanagement.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.integration.outbox.api.OutboxConsumerOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxRetryOperations;
import com.example.taskmanagement.integration.outbox.api.OutboxRetryOperations.Channel;
import com.example.taskmanagement.integration.outbox.api.OutboxMessage;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class OutboxRetryIntegrationTest {
    @Autowired private OutboxOperations events;
    @Autowired private OutboxConsumerOperations consumers;
    @Autowired private OutboxRetryOperations retries;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private Clock clock;
    private final Instant now = Instant.parse("2026-11-06T10:00:00Z");
    private TransactionTemplate transaction;
    private long id;

    @BeforeEach
    void setup() {
        jdbc.update("delete from task_management.outbox_messages");
        when(clock.instant()).thenReturn(now);
        transaction = new TransactionTemplate(transactions);
        events.append("fixture.retry", "{}");
        id = transaction.execute(status -> consumers.claimUnprocessed(100).getFirst().id());
    }

    @ParameterizedTest
    @EnumSource(Channel.class)
    void deferredRowsBecomeEligibleAtThePersistedBoundaryAndDoNotBlockOtherRows(Channel channel) {
        retries.defer(id, channel);
        events.append("fixture.later", "{}");
        assertThat(claim(channel)).hasSize(1).noneMatch(message -> message.id() == id);
        for (var other : Channel.values()) {
            if (other != channel) {
                assertThat(claim(other)).hasSize(2);
            }
        }
        // A new transaction reads the stored deadline; no in-memory dispatcher state is involved.
        when(clock.instant()).thenReturn(now.plusSeconds(59));
        assertThat(claim(channel)).hasSize(1);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThat(claim(channel)).hasSize(2).anyMatch(message -> message.id() == id);
        retries.defer(id, channel);
        when(clock.instant()).thenReturn(now.plusSeconds(359));
        assertThat(claim(channel)).hasSize(1);
        when(clock.instant()).thenReturn(now.plusSeconds(360));
        assertThat(claim(channel)).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(Channel.class)
    void lateFailureCannotRescheduleAnAcknowledgedChannel(Channel channel) {
        transaction.executeWithoutResult(status -> {
            switch (channel) {
                case IN_APP -> consumers.markProcessed(id);
                case EMAIL -> consumers.markEmailProcessed(id);
                case TELEGRAM -> consumers.markTelegramProcessed(id);
            }
        });
        retries.defer(id, channel);
        var column = switch (channel) {
            case IN_APP -> "in_app_failures";
            case EMAIL -> "email_failures";
            case TELEGRAM -> "telegram_failures";
        };
        assertThat(jdbc.queryForObject("select " + column
                + " from task_management.outbox_messages where id = ?", Integer.class, id)).isZero();
        when(clock.instant()).thenReturn(now.plusSeconds(3600));
        assertThat(claim(channel)).isEmpty();
    }

    private List<OutboxMessage> claim(Channel channel) {
        return transaction.execute(status -> switch (channel) {
            case IN_APP -> consumers.claimUnprocessed(100);
            case EMAIL -> consumers.claimEmailUnprocessed(100);
            case TELEGRAM -> consumers.claimTelegramUnprocessed(100);
        });
    }
}
