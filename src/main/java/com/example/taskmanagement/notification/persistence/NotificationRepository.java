package com.example.taskmanagement.notification.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    Page<NotificationEntity> findByUserId(long userId, Pageable pageable);

    Page<NotificationEntity> findByUserIdAndReadAtIsNull(long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationEntity n set n.readAt = :at where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") long userId, @Param("at") Instant at);

    @Query("select n from NotificationEntity n where n.userId = :userId and n.readAt is null")
    List<NotificationEntity> findUnread(@Param("userId") long userId);
}
