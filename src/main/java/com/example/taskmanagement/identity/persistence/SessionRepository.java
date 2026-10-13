package com.example.taskmanagement.identity.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    Optional<SessionEntity> findByTokenHash(String hash);

    @Modifying
    @Query("update SessionEntity s set s.revoked = true, s.version = s.version + 1 where s.userId = :userId")
    void revokeAll(@Param("userId") long userId);
}
