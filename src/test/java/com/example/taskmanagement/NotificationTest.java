package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.example.taskmanagement.service.DeadlineReminderService;
import com.example.taskmanagement.service.TaskEmail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;

class NotificationTest extends ApiTestSupport {
    @Autowired
    private DeadlineReminderService reminders;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired
    private org.springframework.context.ApplicationEventPublisher events;

    @Test
    void assignmentAndCommentNotifyAssigneeOnlyAfterSuccessfulWrite() throws Exception {
        var owner = account();
        var member = account();
        long project = project(owner, member);
        long task = task(owner, project, member.id());
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, timeout(5000)).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly(member.name() + "@example.test");
        assertThat(sent.getValue().getSubject()).isEqualTo("Task assigned");
        clearInvocations(mail);
        json(201, "POST", "/api/comments", owner.token(), Map.of("taskId", task, "text", "Please review"));
        verify(mail, timeout(5000)).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("New task comment");
        clearInvocations(mail);
        json(200, "PUT", "/api/tasks/" + task, owner.token(), taskBody(project, member.id(), List.of()));
        json(201, "POST", "/api/comments", member.token(), Map.of("taskId", task, "text", "My own comment"));
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test
    void dailyRemindersSelectOnlyAssignedOpenTasksDueTodayOrTomorrow() throws Exception {
        var owner = account();
        long project = project(owner);
        for (String date : List.of("2030-02-09", "2030-02-10", "2030-02-11", "2030-02-12")) {
            var body = taskBody(project, owner.id(), List.of());
            body.put("dueDate", date);
            json(201, "POST", "/api/tasks", owner.token(), body);
        }
        var completed = taskBody(project, owner.id(), List.of());
        completed.put("dueDate", "2030-02-10");
        completed.put("status", "COMPLETED");
        json(201, "POST", "/api/tasks", owner.token(), completed);
        var unassigned = taskBody(project, null, List.of());
        unassigned.put("dueDate", "2030-02-10");
        json(201, "POST", "/api/tasks", owner.token(), unassigned);
        verify(mail, timeout(5000).times(5)).send(any(SimpleMailMessage.class));
        clearInvocations(mail);
        reminders.remind();
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, timeout(5000).times(2)).send(sent.capture());
        assertThat(sent.getAllValues()).allSatisfy(message -> {
            assertThat(message.getTo()).containsExactly(owner.name() + "@example.test");
            assertThat(message.getSubject()).isEqualTo("Task deadline reminder");
        });
        assertThat(sent.getAllValues()).extracting(SimpleMailMessage::getText)
                .anyMatch(text -> text.contains("2030-02-10")).anyMatch(text -> text.contains("2030-02-11"));
    }

    @Test
    void rolledBackChangesNeverSendAndSmtpFailureDoesNotUndoSavedTask() throws Exception {
        transactions.executeWithoutResult(status -> {
            events.publishEvent(new TaskEmail("nobody@example.test", "Rollback", "Do not send"));
            status.setRollbackOnly();
        });
        org.mockito.Mockito.verifyNoInteractions(mail);
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        var owner = account();
        long task = task(owner, project(owner), owner.id());
        verify(mail, timeout(5000)).send(any(SimpleMailMessage.class));
        assertThat(json(200, "GET", "/api/tasks/" + task, owner.token(), null).get("id").asLong()).isEqualTo(task);
    }
}
