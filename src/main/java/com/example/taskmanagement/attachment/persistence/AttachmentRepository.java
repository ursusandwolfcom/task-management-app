package com.example.taskmanagement.attachment.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<AttachmentEntity, Long> {
    Page<AttachmentEntity> findByTaskId(long taskId, Pageable pageable);

    @Query("select a.projectId from AttachmentEntity a where a.id = :id")
    Optional<Long> projectId(@Param("id") long id);
}
