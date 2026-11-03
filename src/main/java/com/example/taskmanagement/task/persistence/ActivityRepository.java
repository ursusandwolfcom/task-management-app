package com.example.taskmanagement.task.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<ActivityEntity, Long> {
    Page<ActivityEntity> findByTaskId(long taskId, Pageable pageable);
}
