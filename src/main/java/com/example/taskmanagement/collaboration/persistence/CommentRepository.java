package com.example.taskmanagement.collaboration.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    Page<CommentEntity> findByTaskId(long taskId, Pageable pageable);

    @Query("select c.projectId from CommentEntity c where c.id = :id")
    Optional<Long> projectId(@Param("id") long id);
}
