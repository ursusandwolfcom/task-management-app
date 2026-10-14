package com.example.taskmanagement.task.persistence;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<AttemptEntity, Long> {
    List<AttemptEntity> findByTaskIdOrderByNumberAsc(long taskId);

    Page<AttemptEntity> findByTaskId(long taskId, Pageable pageable);
}
