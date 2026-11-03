package com.example.taskmanagement.integration.outbox.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, Long> {
    @Query("select m from OutboxMessageEntity m where m.processedAt is null order by m.id asc")
    List<OutboxMessageEntity> findUnprocessed(Pageable pageable);

    @Query("select m from OutboxMessageEntity m where m.telegramProcessedAt is null order by m.id asc")
    List<OutboxMessageEntity> findTelegramUnprocessed(Pageable pageable);
}
