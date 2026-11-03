package com.example.taskmanagement.task.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntryEntity, Long> {
    Page<TimeEntryEntity> findByTaskId(long taskId, Pageable pageable);

    @Query("select e.projectId from TimeEntryEntity e where e.id = :id")
    Optional<Long> projectId(@Param("id") long id);

    @Query("select coalesce(sum(e.minutes), 0) from TimeEntryEntity e where e.taskId = :taskId")
    long sumMinutesByTaskId(@Param("taskId") long taskId);
}
