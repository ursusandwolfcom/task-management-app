package com.example.taskmanagement.project.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, Long> {
    boolean existsByProjectIdAndUserId(long projectId, long userId);

    Optional<ProjectMemberEntity> findByProjectIdAndUserId(long projectId, long userId);

    Page<ProjectMemberEntity> findByProjectId(long projectId, Pageable pageable);
}
