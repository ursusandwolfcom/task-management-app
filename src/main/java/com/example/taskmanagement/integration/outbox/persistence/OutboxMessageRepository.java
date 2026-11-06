package com.example.taskmanagement.integration.outbox.persistence;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboxMessageEntity m where m.processedAt is null order by m.id asc")
    List<OutboxMessageEntity> findUnprocessed(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboxMessageEntity m where m.telegramProcessedAt is null order by m.id asc")
    List<OutboxMessageEntity> findTelegramUnprocessed(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboxMessageEntity m where m.emailProcessedAt is null order by m.id asc")
    List<OutboxMessageEntity> findEmailUnprocessed(Pageable pageable);
}
