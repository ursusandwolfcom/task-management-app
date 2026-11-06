package com.example.taskmanagement.service;

import com.example.taskmanagement.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadlineReminderService {
    private static final int REMINDER_PAGE_SIZE = 100;
    private final TaskRepository tasks;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DeadlineReminderService(TaskRepository tasks, ApplicationEventPublisher events, Clock clock) {
        this.tasks = tasks;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(cron = "${notification.reminder-cron:0 0 9 * * *}", zone = "UTC")
    @Transactional(readOnly = true)
    public void remind() {
        LocalDate today = LocalDate.now(clock);
        var page = PageRequest.of(0, REMINDER_PAGE_SIZE);
        while (true) {
            var result = tasks.findReminders(today, today.plusDays(1), page);
            for (var task : result) {
                events.publishEvent(new TaskEmail(task.getAssignee().getEmail(), "Task deadline reminder",
                        task.getName() + " is due on " + task.getDueDate()));
            }
            if (!result.hasNext()) {
                return;
            }
            page = page.next();
        }
    }
}
