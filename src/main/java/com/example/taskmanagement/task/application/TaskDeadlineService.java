package com.example.taskmanagement.task.application;

import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.task.api.TaskDeadlineOperations;
import com.example.taskmanagement.task.domain.TaskStatus;
import com.example.taskmanagement.task.persistence.TaskRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskDeadlineService implements TaskDeadlineOperations {
    private final TaskRepository tasks;
    private final ProjectCoordination projects;
    private final EntityManager entityManager;

    public TaskDeadlineService(TaskRepository tasks, ProjectCoordination projects, EntityManager entityManager) {
        this.tasks = tasks;
        this.projects = projects;
        this.entityManager = entityManager;
    }

    @Override
    public List<Long> scanAfter(long taskId, int limit) {
        return tasks.deadlineCandidates(taskId, PageRequest.of(0, Math.clamp(limit, 1, 100)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Candidate> lockCandidate(long taskId) {
        var projectId = tasks.projectId(taskId);
        if (projectId.isEmpty()) {
            return Optional.empty();
        }
        var project = projects.lockForBackground(projectId.get());
        var row = tasks.findById(taskId);
        if (project.isEmpty() || row.isEmpty()) {
            return Optional.empty();
        }
        entityManager.refresh(row.get());
        var task = row.get().view();
        var parent = project.get();
        return Optional.of(new Candidate(task.id(), task.projectId(), task.name(), task.dueDate(),
                task.assigneeId(), parent.ownerId(), parent.timezone(),
                task.status() != TaskStatus.COMPLETED && task.status() != TaskStatus.CANCELLED));
    }

    @Override
    public boolean mayReceive(long taskId, long projectId, LocalDate dueDate, long recipientId) {
        return tasks.deadlineView(taskId).filter(task -> task.projectId() == projectId
                && dueDate.equals(task.dueDate()) && task.assigneeId() != null
                && task.status() != TaskStatus.COMPLETED && task.status() != TaskStatus.CANCELLED
                && projects.isMember(projectId, recipientId)
                && (task.assigneeId() == recipientId || projects.isCurrentOwner(projectId, recipientId))).isPresent();
    }
}
