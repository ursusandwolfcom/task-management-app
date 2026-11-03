package com.example.taskmanagement.task.application;

import com.example.taskmanagement.planning.core.BreakWindow;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.persistence.DependencyRepository;
import com.example.taskmanagement.task.persistence.TaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskGraphLoader {
    private final TaskRepository tasks;
    private final DependencyRepository dependencies;
    private final ProjectCoordination coordination;
    private final int maxTasks;
    private final int maxDependencies;

    public TaskGraphLoader(TaskRepository tasks, DependencyRepository dependencies, ProjectCoordination coordination,
            @Value("${planning.graph.max-tasks:10000}") int maxTasks,
            @Value("${planning.graph.max-dependencies:50000}") int maxDependencies) {
        if (maxTasks < 1 || maxDependencies < 1 || maxTasks == Integer.MAX_VALUE
                || maxDependencies == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Graph limits must be positive and allow a limit+1 probe");
        }
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.coordination = coordination;
        this.maxTasks = maxTasks;
        this.maxDependencies = maxDependencies;
    }

    @Transactional
    public TaskGraphInput load(long actorId, long projectId) {
        // PostgreSQL FOR UPDATE requires a write-capable transaction even though this use case changes no rows.
        var project = coordination.lockAndAuthorize(actorId, projectId, ProjectCoordination.Permission.READ);
        var nodes = tasks.graphTasks(projectId, PageRequest.of(0, maxTasks + 1));
        if (nodes.size() > maxTasks) {
            throw new TaskException(TaskException.Reason.GRAPH_TOO_LARGE);
        }
        var edges = dependencies.graphEdges(projectId, maxDependencies + 1);
        if (edges.size() > maxDependencies) {
            throw new TaskException(TaskException.Reason.GRAPH_TOO_LARGE);
        }
        var breaks = coordination.breaks(projectId).stream()
                .map(window -> new BreakWindow(window.start(), window.end()))
                .toList();
        return new TaskGraphInput(projectId, project.inputRevision(), project.calendarRevision(),
                project.startDate(), project.endDate(), project.timezone(), coordination.holidays(projectId),
                breaks, nodes, edges);
    }

    int maxDependencies() {
        return maxDependencies;
    }
}
