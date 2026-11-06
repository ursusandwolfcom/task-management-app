package com.example.taskmanagement.notification.persistence;

import com.example.taskmanagement.notification.domain.DeadlineReminderPolicy;
import com.example.taskmanagement.shared.persistence.FiniteDateJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcType;

@Entity
@Table(name = "deadline_reminders", schema = "task_management")
public class DeadlineReminderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id", nullable = false, updatable = false)
    private long taskId;
    @JdbcType(FiniteDateJdbcType.class)
    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;
    @Column(name = "recipient_id", nullable = false, updatable = false)
    private long recipientId;
    @Column(nullable = false, length = 16, updatable = false)
    private String kind;

    protected DeadlineReminderEntity() {
    }

    public DeadlineReminderEntity(long taskId, DeadlineReminderPolicy.Reminder reminder) {
        this.taskId = taskId;
        dueDate = reminder.dueDate();
        recipientId = reminder.recipientId();
        kind = reminder.kind().name();
    }
}
