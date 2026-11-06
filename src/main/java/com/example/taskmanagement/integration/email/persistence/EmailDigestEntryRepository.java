package com.example.taskmanagement.integration.email.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailDigestEntryRepository extends JpaRepository<EmailDigestEntryEntity, Long> {
    @Query("select e.userId from EmailDigestEntryEntity e group by e.userId order by e.userId")
    List<Long> findDistinctUserIds();

    List<EmailDigestEntryEntity> findByUserIdOrderByIdAsc(long userId);

    @Query("select count(e) from EmailDigestEntryEntity e where e.userId in :userIds")
    long countByUserIdIn(@Param("userIds") Collection<Long> userIds);
}
