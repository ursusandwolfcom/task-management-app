package com.example.taskmanagement.task.application;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.planning.core.CriticalPathCalculator;
import com.example.taskmanagement.planning.core.PriorityTopologicalSorter;
import com.example.taskmanagement.planning.core.WorkingCalendar;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.task.api.PortfolioOperations;
import com.example.taskmanagement.task.api.TaskView;
import com.example.taskmanagement.task.domain.TaskStatus;
import com.example.taskmanagement.task.persistence.TaskQueryRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Builds portfolio risk cards from project list + CPM snapshot; no mutations. */
@Service
@Transactional(propagation = Propagation.NEVER)
public class PortfolioService implements PortfolioOperations {
    private static final EnumSet<TaskStatus> OPEN = EnumSet.of(
            TaskStatus.NOT_STARTED, TaskStatus.IN_PROGRESS, TaskStatus.FAILED);

    private final ProjectOperations projects;
    private final TaskGraphLoader loader;
    private final TaskQueryRepository queries;
    private final IdentityOperations identity;
    private final CriticalPathCalculator criticalPath = new CriticalPathCalculator(new PriorityTopologicalSorter());

    public PortfolioService(ProjectOperations projects, TaskGraphLoader loader, TaskQueryRepository queries,
            IdentityOperations identity) {
        this.projects = projects;
        this.loader = loader;
        this.queries = queries;
        this.identity = identity;
    }

    @Override
    public PortfolioPage overview(long actorId, int page, int size) {
        var listed = projects.list(actorId, page, size);
        var cards = new ArrayList<ProjectRisk>(listed.content().size());
        for (var project : listed.content()) {
            var input = loader.load(actorId, project.id());
            var calendar = new WorkingCalendar(ZoneId.of(input.timezone()), input.holidays(), input.breaks());
            var schedule = criticalPath.schedule(input.graph(), calendar, calendar.projectOrigin(input.startDate()),
                    input.endDate());
            long open = input.tasks().stream().filter(task -> OPEN.contains(task.status())).count();
            long critical = schedule.criticalTaskIds().size();
            boolean taskOverrun = schedule.tasks().stream().anyMatch(task -> task.deadlineOverrun());
            Risk risk = schedule.projectDeadlineOverrun() ? Risk.OVERDUE
                    : taskOverrun ? Risk.AT_RISK : Risk.ON_TRACK;
            long planned = input.tasks().stream().mapToLong(PortfolioService::estimateOrZero).sum();
            long completedPlanned = input.tasks().stream()
                    .filter(task -> task.status() == TaskStatus.COMPLETED)
                    .mapToLong(PortfolioService::estimateOrZero).sum();
            int completionPercent = planned == 0 ? 0 : (int) Math.min(100L, (completedPlanned * 100L) / planned);
            cards.add(new ProjectRisk(project.id(), project.name(), project.status().name(), project.timezone(),
                    project.endDate(), open, critical, schedule.durationMinutes(),
                    schedule.projectDeadlineOverrun(), taskOverrun, risk, planned, completedPlanned,
                    completionPercent));
        }
        return new PortfolioPage(Instant.now(), cards, listed.page(), listed.size(), listed.totalElements(),
                listed.totalPages());
    }

    @Override
    public WorkloadSnapshot workload(long actorId) {
        boolean admin = identity.currentRole(actorId) == UserRole.ADMIN;
        var contendedByAssignee = contendedOpenTasksByAssignee(actorId);
        var rows = queries.workload(actorId, admin).stream()
                .map(row -> new AssigneeLoad(row.userId(), row.openTasks(), row.estimatedMinutes(),
                        row.overdueTasks(), contendedByAssignee.getOrDefault(row.userId(), 0L)))
                .toList();
        return new WorkloadSnapshot(Instant.now(), rows);
    }

    private java.util.Map<Long, Long> contendedOpenTasksByAssignee(long actorId) {
        var counts = new java.util.HashMap<Long, Long>();
        var listed = projects.list(actorId, 0, 100);
        for (var project : listed.content()) {
            var input = loader.load(actorId, project.id());
            var calendar = new WorkingCalendar(ZoneId.of(input.timezone()), input.holidays(), input.breaks());
            var schedule = criticalPath.schedule(input.graph(), calendar, calendar.projectOrigin(input.startDate()),
                    input.endDate());
            var assignees = new java.util.HashMap<Long, Long>();
            var openIds = new java.util.HashSet<Long>();
            for (var task : input.tasks()) {
                if (OPEN.contains(task.status()) && task.assigneeId() != null) {
                    assignees.put(task.id(), task.assigneeId());
                    openIds.add(task.id());
                }
            }
            var openSchedules = schedule.tasks().stream().filter(task -> openIds.contains(task.taskId())).toList();
            for (Long taskId : ResourceContention.contendedTaskIds(openSchedules, assignees)) {
                Long assignee = assignees.get(taskId);
                if (assignee != null) {
                    counts.merge(assignee, 1L, Long::sum);
                }
            }
        }
        return counts;
    }

    private static long estimateOrZero(TaskView task) {
        return task.estimatedMinutes() == null ? 0L : task.estimatedMinutes().longValue();
    }
}
