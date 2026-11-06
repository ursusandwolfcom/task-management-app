package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.CommentRequest;
import com.example.taskmanagement.dto.CommentResponse;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Comment;
import com.example.taskmanagement.model.Role;
import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.repository.CommentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CommentService {
    private final CommentRepository comments;
    private final TaskService tasks;
    private final UserService users;
    private final ResponseMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CommentService(CommentRepository comments, TaskService tasks, UserService users,
            ResponseMapper mapper, ApplicationEventPublisher events, Clock clock) {
        this.comments = comments;
        this.tasks = tasks;
        this.users = users;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    public CommentResponse create(Long actorId, CommentRequest request) {
        Task task = tasks.writable(actorId, request.taskId());
        var comment = new Comment();
        comment.setTask(task);
        comment.setUser(users.getUser(actorId));
        comment.setText(request.text());
        comment.setTimestamp(LocalDateTime.now(clock));
        comments.saveAndFlush(comment);
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(actorId)) {
            events.publishEvent(new TaskEmail(task.getAssignee().getEmail(), "New task comment",
                    "New comment on: " + task.getName()));
        }
        return mapper.toResponse(comment);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> list(Long actorId, Long taskId, Pageable pageable) {
        tasks.accessible(actorId, taskId);
        return comments.findByTaskId(taskId, pageable).map(mapper::toResponse);
    }

    public void delete(Long actorId, Long id) {
        Comment comment = comments.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        Task task = tasks.writable(actorId, comment.getTask().getId());
        if (!comment.getUser().getId().equals(actorId) && !task.getProject().getOwner().getId().equals(actorId)
                && users.getUser(actorId).getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comment author or project owner required");
        }
        comments.delete(comment);
    }
}
