package com.example.taskmanagement.collaboration.application;

import com.example.taskmanagement.task.api.ActivityEvents;
import com.example.taskmanagement.task.api.ActivityOperations;
import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.collaboration.api.CommentData;
import com.example.taskmanagement.collaboration.api.CommentOperations;
import com.example.taskmanagement.collaboration.api.CommentView;
import com.example.taskmanagement.collaboration.persistence.CommentEntity;
import com.example.taskmanagement.collaboration.persistence.CommentRepository;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.integration.outbox.api.OutboxEvents;
import com.example.taskmanagement.integration.outbox.api.OutboxOperations;
import com.example.taskmanagement.shared.persistence.PageRequests;
import com.example.taskmanagement.task.api.TaskOperations;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@Transactional
public class CommentService implements CommentOperations {
    private static final int PREVIEW_CHARACTERS = 200;
    private final CommentRepository comments;
    private final CollaborationAccess access;
    private final IdentityOperations identity;
    private final TaskOperations tasks;
    private final OutboxOperations outbox;
    private final ActivityOperations activity;
    private final ObjectMapper json;
    private final Clock clock;

    public CommentService(CommentRepository comments, CollaborationAccess access, IdentityOperations identity,
            TaskOperations tasks, OutboxOperations outbox, ActivityOperations activity, ObjectMapper json,
            Clock clock) {
        this.comments = comments;
        this.access = access;
        this.identity = identity;
        this.tasks = tasks;
        this.outbox = outbox;
        this.activity = activity;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public CommentView create(long actorId, CommentData data) {
        var project = access.lockTask(actorId, data.taskId());
        var created = comments.saveAndFlush(new CommentEntity(project.id(), data.taskId(), actorId, data.text(),
                clock.instant().truncatedTo(ChronoUnit.MICROS))).view();
        var task = tasks.read(actorId, data.taskId());
        activity.record(actorId, project.id(), data.taskId(), ActivityEvents.COMMENT_CREATED, "Добавлен комментарий");
        var body = new LinkedHashMap<String, Object>();
        body.put("authorId", actorId);
        body.put("assigneeId", task.assigneeId());
        body.put("projectId", project.id());
        body.put("taskId", data.taskId());
        body.put("preview", preview(data.text()));
        outbox.append(OutboxEvents.COMMENT_CREATED, json.writeValueAsString(body));
        return created;
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
        activity.record(actorId, projectId, comment.view().taskId(), ActivityEvents.COMMENT_DELETED,
                "Удалён комментарий");
        comments.delete(comment);
        comments.flush();
    }

    private static String preview(String text) {
        int end = Math.min(PREVIEW_CHARACTERS, text.length());
        // Fit the existing push preview budget without splitting a supplementary character.
        if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return text.substring(0, end);
    }

    private static CollaborationException error(CollaborationException.Reason reason) {
        return new CollaborationException(reason);
    }
}
