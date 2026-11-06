package com.example.taskmanagement.repository;

import com.example.taskmanagement.model.Task;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    boolean existsByProjectId(Long projectId);

    boolean existsByProjectIdAndAssigneeId(Long projectId, Long assigneeId);

    @Query("select t from Task t join fetch t.assignee where t.status <> "
            + "com.example.taskmanagement.model.TaskStatus.COMPLETED and "
            + "(t.dueDate = :today or t.dueDate = :tomorrow) order by t.id")
    Slice<Task> findReminders(@Param("today") LocalDate today,
            @Param("tomorrow") LocalDate tomorrow, Pageable pageable);

    @Query("select t from Task t join t.labels l where l.id = :labelId")
    List<Task> findWithLabel(@Param("labelId") Long labelId);
}
