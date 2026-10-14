package com.example.taskmanagement.task.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    Page<TaskEntity> findByProjectId(long projectId, Pageable pageable);

    boolean existsByProjectId(long projectId);

    boolean existsByProjectIdAndAssigneeId(long projectId, long userId);

    @Query("select t.projectId from TaskEntity t where t.id = :id")
    Optional<Long> projectId(@Param("id") long id);

    @Query(value = """
            select count(*) from task_management.task_dependencies d
            join task_management.tasks p on p.id = d.depends_on_task_id
            where d.task_id = :id and p.status <> 'COMPLETED'
            """, nativeQuery = true)
    long incompletePredecessors(@Param("id") long id);

    @Query(value = "select count(*) from task_management.task_dependencies where depends_on_task_id = :id",
            nativeQuery = true)
    long dependentCount(@Param("id") long id);

    @Query(value = "delete from task_management.task_dependencies where task_id = :id", nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    void deleteIncomingEdges(@Param("id") long id);
}
