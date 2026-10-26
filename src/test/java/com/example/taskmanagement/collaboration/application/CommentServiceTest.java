package com.example.taskmanagement.collaboration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.collaboration.api.CommentData;
import com.example.taskmanagement.collaboration.persistence.CommentEntity;
import com.example.taskmanagement.collaboration.persistence.CommentRepository;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.project.api.ProjectStatus;
import com.example.taskmanagement.project.api.ProjectView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CommentServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00.123456789Z");
    private final CommentRepository comments = mock(CommentRepository.class);
    private final CollaborationAccess access = mock(CollaborationAccess.class);
    private final IdentityOperations identity = mock(IdentityOperations.class);
    private final CommentService service = new CommentService(comments, access, identity,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void commentUsesAuthenticatedAuthorAndServerMicrosAndKeepsMarkupAsPlainText() {
        when(access.lockTask(2, 3)).thenReturn(project());
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
        verify(comments, never()).delete(any());
    }

    private static CommentEntity stored() {
        var comment = new CommentEntity(10, 3, 2, "Text", NOW);
        ReflectionTestUtils.setField(comment, "id", 4L);
        return comment;
    }

    private static ProjectView project() {
        return new ProjectView(10, "Project", "", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                "UTC", ProjectStatus.INITIATED, 1, 0, 0, 0);
    }
}
