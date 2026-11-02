package com.example.taskmanagement.task.application;

import com.example.taskmanagement.planning.core.DependencyEdge;
import com.example.taskmanagement.planning.core.ExecutionFacts;
import com.example.taskmanagement.planning.core.GraphSnapshot;
import com.example.taskmanagement.planning.core.TaskNode;
import com.example.taskmanagement.task.api.TaskBucket;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Detached immutable facts, captured under the project lock and calculated after the read transaction. */
record TaskGraphInput(long projectId, long inputRevision, List<TaskView> tasks, List<DependencyEdge> edges) {
    TaskGraphInput {
        tasks = List.copyOf(tasks);
        edges = List.copyOf(edges);
    }

    GraphSnapshot graph() {
        return GraphSnapshot.of(tasks.stream().map(TaskGraphInput::node).toList(), edges);
    }

    private static TaskNode node(TaskView task) {
        int weight = switch (task.priority()) {
            case LOW -> 10;
            case MEDIUM -> 20;
            case HIGH -> 30;
            case CRITICAL -> 40;
        };
        return new TaskNode(task.id(), weight, task.dueDate(),
                task.estimatedMinutes() == null ? null : task.estimatedMinutes().longValue());
    }

    ExecutionFacts facts() {
        // Ready queue considers only WORK; completed predecessors may sit in any bucket.
        return new ExecutionFacts(idsWithStatus(TaskStatus.COMPLETED), tasks.stream()
                .filter(task -> task.status() == TaskStatus.NOT_STARTED && task.bucket() == TaskBucket.WORK)
                .map(TaskView::id).collect(Collectors.toSet()));
    }

    Map<Long, TaskView> viewsById() {
        return tasks.stream().collect(Collectors.toUnmodifiableMap(TaskView::id, Function.identity()));
    }

    private java.util.Set<Long> idsWithStatus(TaskStatus status) {
        return tasks.stream().filter(task -> task.status() == status).map(TaskView::id).collect(Collectors.toSet());
    }
}
