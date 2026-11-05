package com.example.taskmanagement.notification.persistence;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadlineReminderRepository extends JpaRepository<DeadlineReminderEntity, Long> {
    boolean existsByTaskIdAndDueDateAndRecipientIdAndKind(long taskId, LocalDate dueDate,
            long recipientId, String kind);
}
