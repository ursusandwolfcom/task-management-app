package com.example.taskmanagement.collaboration.application;

import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.collaboration.api.CommentData;
import com.example.taskmanagement.collaboration.api.CommentOperations;
import com.example.taskmanagement.collaboration.api.CommentView;
import com.example.taskmanagement.collaboration.persistence.CommentEntity;
import com.example.taskmanagement.collaboration.persistence.CommentRepository;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.shared.persistence.PageRequests;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class CommentService implements CommentOperations {
    private final CommentRepository comments;
    private final CollaborationAccess access;
    private final IdentityOperations identity;
    private final Clock clock;

    public CommentService(CommentRepository comments, CollaborationAccess access, IdentityOperations identity,
            Clock clock) {
        this.comments = comments;
        this.access = access;
        this.identity = identity;
        this.clock = clock;
    }

    @Override
    public CommentView create(long actorId, CommentData data) {
        var project = access.lockTask(actorId, data.taskId());
        return comments.saveAndFlush(new CommentEntity(project.id(), data.taskId(), actorId, data.text(),
                clock.instant().truncatedTo(ChronoUnit.MICROS))).view();
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage list(long actorId, long taskId, int page, int size) {
        access.task(actorId, taskId);
        var paging = PageRequests.descending(page, size, "id",
                () -> error(CollaborationException.Reason.VALIDATION_ERROR));
        var result = comments.findByTaskId(taskId, paging);
        return new CommentPage(result.getContent().stream().map(CommentEntity::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public void delete(long actorId, long commentId) {
        long projectId = comments.projectId(commentId)
                .orElseThrow(() -> error(CollaborationException.Reason.COMMENT_NOT_FOUND));
        var project = access.lockProject(actorId, projectId, CollaborationException.Reason.COMMENT_NOT_FOUND);
        // Fresh scalar existence after the lock; then load the row (may already be in the persistence context).
        comments.projectId(commentId).orElseThrow(() -> error(CollaborationException.Reason.COMMENT_NOT_FOUND));
        var comment = comments.findById(commentId)
                .orElseThrow(() -> error(CollaborationException.Reason.COMMENT_NOT_FOUND));
        if (actorId != comment.view().authorId() && actorId != project.ownerId()
                && identity.currentRole(actorId) != UserRole.ADMIN) {
            throw error(CollaborationException.Reason.FORBIDDEN);
        }
        comments.delete(comment);
        comments.flush();
    }

    private static CollaborationException error(CollaborationException.Reason reason) {
        return new CollaborationException(reason);
    }
}
