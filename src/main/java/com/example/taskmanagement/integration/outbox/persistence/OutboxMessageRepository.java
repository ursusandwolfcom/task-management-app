package com.example.taskmanagement.integration.outbox.persistence;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboxMessageEntity m where m.processedAt is null "
            + "and (m.inAppNextAttemptAt is null or m.inAppNextAttemptAt <= :now) order by m.id asc")
    List<OutboxMessageEntity> findUnprocessed(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboxMessageEntity m where m.telegramProcessedAt is null "
            + "and (m.telegramNextAttemptAt is null or m.telegramNextAttemptAt <= :now) order by m.id asc")
    List<OutboxMessageEntity> findTelegramUnprocessed(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboxMessageEntity m where m.emailProcessedAt is null "
            + "and (m.emailNextAttemptAt is null or m.emailNextAttemptAt <= :now) order by m.id asc")
    List<OutboxMessageEntity> findEmailUnprocessed(@Param("now") Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboxMessageEntity m where m.id = :id")
    Optional<OutboxMessageEntity> lockById(@Param("id") long id);
}
