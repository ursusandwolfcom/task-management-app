package com.example.taskmanagement.integration.email.persistence;

import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailDigestEntryRepository extends JpaRepository<EmailDigestEntryEntity, Long> {
    @Query("select e.userId from EmailDigestEntryEntity e group by e.userId order by e.userId")
    List<Long> findDistinctUserIds();

    /** Hold the selected entries through send and cleanup; concurrent flushes wait for the outcome. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailDigestEntryEntity> findByUserIdOrderByIdAsc(long userId);

    @Query("select count(e) from EmailDigestEntryEntity e where e.userId in :userIds")
    long countByUserIdIn(@Param("userIds") Collection<Long> userIds);
}
