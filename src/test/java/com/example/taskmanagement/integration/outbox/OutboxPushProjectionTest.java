package com.example.taskmanagement.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxPushProjection;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OutboxPushProjectionTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void recipientsExcludeActorAndIncludeOwnerOnComplete() throws Exception {
        var payload = json.readTree("""
                {"assigneeId":2,"ownerId":1,"actorId":2,"taskId":9,"taskName":"Done"}
                """);
        assertThat(OutboxPushProjection.recipientIds(OutboxEvents.TASK_COMPLETED, payload))
                .isEqualTo(Set.of(2L, 1L));
        var recipients = OutboxPushProjection.recipientIds(OutboxEvents.TASK_COMPLETED, payload);
        recipients.remove(OutboxPushProjection.actorId(OutboxEvents.TASK_COMPLETED, payload));
        assertThat(recipients).containsExactly(1L);
        var rendered = OutboxPushProjection.render(OutboxEvents.TASK_COMPLETED, payload, "https://app.example.com");
        assertThat(rendered.title()).contains("завершена");
        assertThat(rendered.body()).contains("https://app.example.com/#/tasks/9");
    }
}
