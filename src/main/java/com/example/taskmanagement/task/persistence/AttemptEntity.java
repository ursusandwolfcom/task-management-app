package com.example.taskmanagement.task.persistence;

import com.example.taskmanagement.task.domain.ExecutionAttempt;
import com.example.taskmanagement.task.domain.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "task_execution_attempts", schema = "task_management")
public class AttemptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id", nullable = false, updatable = false)
    private long taskId;
    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int number;
    @Column(name = "actual_start", nullable = false, updatable = false)
    private Instant actualStart;
    @Column(name = "actual_end")
    private Instant actualEnd;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskStatus outcome;

    protected AttemptEntity() {
    }

    public AttemptEntity(long taskId, ExecutionAttempt attempt) {
        this.taskId = taskId;
        number = attempt.number();
        actualStart = attempt.actualStart();
        finish(attempt);
    }

    public ExecutionAttempt view() {
        return new ExecutionAttempt(number, actualStart, actualEnd, outcome);
    }

    public void finish(ExecutionAttempt attempt) {
        actualEnd = attempt.actualEnd();
        outcome = attempt.outcome();
    }
}
