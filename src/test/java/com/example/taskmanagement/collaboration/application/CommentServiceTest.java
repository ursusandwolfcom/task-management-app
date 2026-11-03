package com.example.taskmanagement.collaboration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.task.api.ActivityOperations;
import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.collaboration.api.CommentData;
import com.example.taskmanagement.collaboration.persistence.CommentEntity;
import com.example.taskmanagement.collaboration.persistence.CommentRepository;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxOperations;
import com.example.taskmanagement.project.api.ProjectStatus;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskOperations;
import com.example.taskmanagement.task.api.TaskPriority;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class CommentServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00.123456789Z");
    private final CommentRepository comments = mock(CommentRepository.class);
    private final CollaborationAccess access = mock(CollaborationAccess.class);
    private final IdentityOperations identity = mock(IdentityOperations.class);
    private final TaskOperations tasks = mock(TaskOperations.class);
    private final OutboxOperations outbox = mock(OutboxOperations.class);
    private final ActivityOperations activity = mock(ActivityOperations.class);
    private final CommentService service = new CommentService(comments, access, identity, tasks, outbox, activity,
            JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void commentUsesAuthenticatedAuthorAndServerMicrosAndKeepsMarkupAsPlainText() {
        when(access.lockTask(2, 3)).thenReturn(project());
        when(tasks.read(2, 3)).thenReturn(task(5L));
        when(comments.saveAndFlush(any())).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, CommentEntity.class);
            ReflectionTestUtils.setField(entity, "id", 4L);
            return entity;
        });
        var created = service.create(2, new CommentData(3, "  <script>alert('text')</script>  "));
        assertThat(created.authorId()).isEqualTo(2);
        assertThat(created.taskId()).isEqualTo(3);
        assertThat(created.projectId()).isEqualTo(10);
        assertThat(created.createdAt()).isEqualTo(Instant.parse("2026-10-26T10:00:00.123456Z"));
        assertThat(created.text()).isEqualTo("<script>alert('text')</script>");
        verify(outbox).append(eq(OutboxEvents.COMMENT_CREATED), anyString());
    }

    @Test
    void ordinaryMemberCannotDeleteAnotherAuthorsComment() {
        var comment = stored();
        when(comments.projectId(4)).thenReturn(Optional.of(10L));
        when(access.lockProject(3, 10, CollaborationException.Reason.COMMENT_NOT_FOUND)).thenReturn(project());
        when(comments.findById(4L)).thenReturn(Optional.of(comment));
        when(identity.currentRole(3)).thenReturn(UserRole.USER);
        assertThatThrownBy(() -> service.delete(3, 4)).isInstanceOf(CollaborationException.class)
                .extracting("reason").isEqualTo(CollaborationException.Reason.FORBIDDEN);
        verify(comments, never()).delete(any());
    }

    @Test
    void authorOwnerAndCurrentAdminCanDeleteUnderTheCheckedProjectAccess() {
        for (long actor : new long[] {1, 2, 3}) {
            var comment = stored();
            when(comments.projectId(4)).thenReturn(Optional.of(10L));
            when(access.lockProject(actor, 10, CollaborationException.Reason.COMMENT_NOT_FOUND)).thenReturn(project());
            when(comments.findById(4L)).thenReturn(Optional.of(comment));
            when(identity.currentRole(3)).thenReturn(UserRole.ADMIN);
            service.delete(actor, 4);
            verify(comments).delete(comment);
        }
    }

    @Test
    void vanishedCommentIsRejectedAfterWaitingBeforeUsingAnyCachedEntity() {
        when(comments.projectId(4)).thenReturn(Optional.of(10L), Optional.empty());
        when(access.lockProject(2, 10, CollaborationException.Reason.COMMENT_NOT_FOUND)).thenReturn(project());
        assertThatThrownBy(() -> service.delete(2, 4)).isInstanceOf(CollaborationException.class)
                .extracting("reason").isEqualTo(CollaborationException.Reason.COMMENT_NOT_FOUND);
        verify(comments, never()).findById(any());
    }

    private static ProjectView project() {
        return new ProjectView(10, "Project", "", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "UTC",
                ProjectStatus.IN_PROGRESS, 1, 0, 0, 0);
    }

    private static TaskView task(Long assigneeId) {
        return new TaskView(3, 10, "Task", "", TaskPriority.MEDIUM, 15, null, assigneeId, TaskBucket.WORK,
                TaskStatus.NOT_STARTED, null, null, 0, 0);
    }

    private CommentEntity stored() {
        var entity = new CommentEntity(10, 3, 2, "text", NOW);
        ReflectionTestUtils.setField(entity, "id", 4L);
        return entity;
    }
}
