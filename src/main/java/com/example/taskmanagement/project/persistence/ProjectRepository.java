package com.example.taskmanagement.project.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
    boolean existsByIdAndOwnerId(long id, long ownerId);

    @Query(value = "select id from task_management.projects where id = :id for update", nativeQuery = true)
    Optional<Long> lockId(@Param("id") long id);

    @Query("""
            select p from ProjectEntity p where :admin = true or p.ownerId = :actorId
            or exists (select m.id from ProjectMemberEntity m where m.projectId = p.id and m.userId = :actorId)
            """)
    Page<ProjectEntity> accessible(@Param("actorId") long actorId, @Param("admin") boolean admin, Pageable pageable);
}
