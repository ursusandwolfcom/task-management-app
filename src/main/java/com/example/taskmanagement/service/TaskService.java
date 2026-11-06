package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.TaskRequest;
import com.example.taskmanagement.dto.TaskResponse;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Project;
import com.example.taskmanagement.model.Task;
import com.example.taskmanagement.model.User;
import com.example.taskmanagement.repository.AttachmentRepository;
import com.example.taskmanagement.repository.CommentRepository;
import com.example.taskmanagement.repository.LabelRepository;
import com.example.taskmanagement.repository.TaskRepository;
import java.util.HashSet;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TaskService {
    private final TaskRepository tasks;
    private final ProjectService projects;
    private final UserService users;
    private final LabelRepository labels;
    private final CommentRepository comments;
    private final AttachmentRepository attachments;
    private final ResponseMapper mapper;
    private final ApplicationEventPublisher events;

    public TaskService(TaskRepository tasks, ProjectService projects, UserService users,
            LabelRepository labels, CommentRepository comments, AttachmentRepository attachments,
            ResponseMapper mapper, ApplicationEventPublisher events) {
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
        this.labels = labels;
        this.comments = comments;
        this.attachments = attachments;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Task accessible(Long actorId, Long id) {
        Task task = found(id);
        projects.accessible(actorId, task.getProject().getId(), false);
        return task;
    }

    public Task writable(Long actorId, Long id) {
        Task task = found(id);
        projects.lock(actorId, task.getProject().getId(), false);
        return task;
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long actorId, Long id) {
        return mapper.toResponse(accessible(actorId, id));
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(Long actorId, Long projectId, Pageable pageable) {
        projects.accessible(actorId, projectId, false);
        return tasks.findByProjectIdOrderByIdDesc(projectId, pageable).map(mapper::toResponse);
    }

    public TaskResponse create(Long actorId, TaskRequest request) {
        Project project = projects.lock(actorId, request.projectId(), false);
        var task = new Task();
        task.setProject(project);
        apply(task, request);
        return mapper.toResponse(tasks.saveAndFlush(task));
    }

    public TaskResponse update(Long actorId, Long id, TaskRequest request) {
        Task task = writable(actorId, id);
        if (!task.getProject().getId().equals(request.projectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task project cannot change");
        }
        apply(task, request);
        return mapper.toResponse(tasks.saveAndFlush(task));
    }

    public void delete(Long actorId, Long id) {
        Task task = found(id);
        projects.lock(actorId, task.getProject().getId(), true);
        if (attachments.existsByTaskId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delete Dropbox attachments first");
        }
        comments.deleteByTaskId(id);
        tasks.delete(task);
    }

    private Task found(Long id) {
        return tasks.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private void apply(Task task, TaskRequest request) {
        User assignee = request.assigneeId() == null ? null : users.getUser(request.assigneeId());
        Project project = task.getProject();
        if (assignee != null && !project.getOwner().getId().equals(assignee.getId())
                && project.getMembers().stream().noneMatch(user -> user.getId().equals(assignee.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignee must belong to project");
        }
        var selectedLabels = labels.findAllById(request.labelIds());
        if (selectedLabels.size() != request.labelIds().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found");
        }
        Long previous = task.getAssignee() == null ? null : task.getAssignee().getId();
        task.setName(request.name().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setStatus(request.status());
        task.setDueDate(request.dueDate());
        task.setAssignee(assignee);
        task.setLabels(new HashSet<>(selectedLabels));
        if (assignee != null && !Objects.equals(previous, assignee.getId())) {
            events.publishEvent(new TaskEmail(assignee.getEmail(), "Task assigned", "Assigned: " + task.getName()));
        }
    }
}
