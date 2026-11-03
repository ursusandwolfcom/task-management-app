package com.example.taskmanagement.task.application;

import com.example.taskmanagement.planning.core.CriticalPathCalculator;
import com.example.taskmanagement.planning.core.GraphSnapshot;
import com.example.taskmanagement.planning.core.PriorityTopologicalSorter;
import com.example.taskmanagement.planning.core.ProjectSchedule;
import com.example.taskmanagement.planning.core.ReadinessCalculator;
import com.example.taskmanagement.planning.core.ResourceLeveler;
import com.example.taskmanagement.planning.core.TaskNode;
import com.example.taskmanagement.planning.core.TaskSchedule;
import com.example.taskmanagement.planning.core.WorkingCalendar;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TaskPlanningOperations;
import com.example.taskmanagement.task.api.TaskView;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The loader's transaction ends before algorithms run; no calculation reserves or mutates tasks. */
@Service
@Transactional(propagation = Propagation.NEVER)
public class TaskPlanningService implements TaskPlanningOperations {
    private final TaskGraphLoader loader;
    private final PriorityTopologicalSorter sorter = new PriorityTopologicalSorter();
    private final ReadinessCalculator readiness = new ReadinessCalculator(sorter);
    private final CriticalPathCalculator criticalPath = new CriticalPathCalculator(sorter);

    public TaskPlanningService(TaskGraphLoader loader) {
        this.loader = loader;
    }

    @Override
    public ExecutionOrder executionOrder(long actorId, long projectId) {
        var input = loader.load(actorId, projectId);
        var order = sorter.executionOrder(input.graph()).stream().map(TaskNode::id).toList();
        return new ExecutionOrder(projectId, input.inputRevision(), order);
    }

    @Override
    public AvailableTasks availableTasks(long actorId, long projectId) {
        var input = loader.load(actorId, projectId);
        var graph = input.graph();
        var facts = input.facts();
        var views = input.viewsById();
        var available = readiness.availableTasks(graph, facts).stream().map(node -> views.get(node.id())).toList();
        var blocked = facts.candidateIds().stream().sorted().map(id -> new BlockedTask(id,
                "DEPENDENCIES_NOT_COMPLETED", graph.predecessors(id).stream()
                        .filter(predecessor -> !facts.completedIds().contains(predecessor)).sorted().toList()))
                .filter(reason -> !reason.predecessorIds().isEmpty()).toList();
        return new AvailableTasks(projectId, input.inputRevision(), available, blocked);
    }

    @Override
    public NextTask nextTask(long actorId, long projectId) {
        var input = loader.load(actorId, projectId);
        var views = input.viewsById();
        var next = readiness.nextTask(input.graph(), input.facts()).map(node -> views.get(node.id())).orElse(null);
        return new NextTask(projectId, input.inputRevision(), next);
    }

    @Override
    public ProjectGantt gantt(long actorId, long projectId, boolean resourceLeveling) {
        var input = loader.load(actorId, projectId);
        var calendar = new WorkingCalendar(ZoneId.of(input.timezone()), input.holidays(), input.breaks());
        return toGantt(projectId, input, calendar, criticalPath.schedule(input.graph(), calendar,
                calendar.projectOrigin(input.startDate()), input.endDate()), resourceLeveling);
    }

    @Override
    public ProjectGantt whatIf(long actorId, long projectId, WhatIfRequest request, boolean resourceLeveling) {
        var input = loader.load(actorId, projectId);
        var known = input.viewsById().keySet();
        var overrides = new HashMap<Long, Integer>();
        var seen = new HashSet<Long>();
        for (var item : request.estimates()) {
            if (!known.contains(item.taskId())) {
                throw new TaskException(TaskException.Reason.TASK_NOT_FOUND);
            }
            if (!seen.add(item.taskId())) {
                throw new TaskException(TaskException.Reason.VALIDATION_ERROR);
            }
            overrides.put(item.taskId(), item.estimatedMinutes());
        }
        var calendar = new WorkingCalendar(ZoneId.of(input.timezone()), input.holidays(), input.breaks());
        var graph = GraphSnapshot.of(input.tasks().stream().map(task -> node(task, overrides)).toList(), input.edges());
        return toGantt(projectId, input, calendar, criticalPath.schedule(graph, calendar,
                calendar.projectOrigin(input.startDate()), input.endDate()), resourceLeveling);
    }

    private static TaskNode node(TaskView task, Map<Long, Integer> overrides) {
        int weight = switch (task.priority()) {
            case LOW -> 10;
            case MEDIUM -> 20;
            case HIGH -> 30;
            case CRITICAL -> 40;
        };
        Long minutes;
        if (overrides.containsKey(task.id())) {
            Integer override = overrides.get(task.id());
            minutes = override == null ? null : override.longValue();
        } else {
            minutes = task.estimatedMinutes() == null ? null : task.estimatedMinutes().longValue();
        }
        return new TaskNode(task.id(), weight, task.dueDate(), minutes);
    }

    private static ProjectGantt toGantt(long projectId, TaskGraphInput input, WorkingCalendar calendar,
            ProjectSchedule planned, boolean resourceLeveling) {
        Map<Long, Long> assignees = new HashMap<>();
        for (TaskView view : input.tasks()) {
            if (view.assigneeId() != null) {
                assignees.put(view.id(), view.assigneeId());
            }
        }
        List<TaskSchedule> scheduleTasks = planned.tasks();
        if (resourceLeveling) {
            scheduleTasks = ResourceLeveler.level(scheduleTasks, assignees, calendar);
        }
        var contended = ResourceContention.contendedTaskIds(scheduleTasks, assignees);
        Instant projectFinish = scheduleTasks.stream().map(TaskSchedule::earlyFinish).max(Instant::compareTo)
                .orElse(planned.projectFinish());
        long durationMinutes = calendar.workingMinutesBetween(planned.projectStart(), projectFinish);
        var tasks = scheduleTasks.stream()
                .map(task -> new GanttTask(task.taskId(), task.durationMinutes(), task.earlyStart(),
                        task.earlyFinish(), task.lateStart(), task.lateFinish(), task.floatMinutes(),
                        task.critical(), task.deadlineOverrun(), contended.contains(task.taskId())))
                .toList();
        var edges = planned.criticalEdges().stream()
                .map(edge -> new CriticalDependency(edge.predecessorId(), edge.successorId()))
                .toList();
        var criticalIds = scheduleTasks.stream().filter(TaskSchedule::critical).map(TaskSchedule::taskId).toList();
        return new ProjectGantt(projectId, input.inputRevision(), input.calendarRevision(), input.timezone(),
                Instant.now(), planned.projectStart(), projectFinish, durationMinutes,
                planned.projectDeadlineOverrun(), tasks, criticalIds, edges);
    }
}
