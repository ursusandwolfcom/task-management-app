package com.example.taskmanagement.task.application;

import com.example.taskmanagement.planning.core.CriticalPathCalculator;
import com.example.taskmanagement.planning.core.PriorityTopologicalSorter;
import com.example.taskmanagement.planning.core.WorkingCalendar;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.task.api.PortfolioOperations;
import com.example.taskmanagement.task.domain.TaskStatus;
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
    private final CriticalPathCalculator criticalPath = new CriticalPathCalculator(new PriorityTopologicalSorter());

    public PortfolioService(ProjectOperations projects, TaskGraphLoader loader) {
        this.projects = projects;
        this.loader = loader;
    }

    @Override
    public PortfolioPage overview(long actorId, int page, int size) {
        var listed = projects.list(actorId, page, size);
        var cards = new ArrayList<ProjectRisk>(listed.content().size());
        for (var project : listed.content()) {
            var input = loader.load(actorId, project.id());
            var calendar = new WorkingCalendar(ZoneId.of(input.timezone()));
            var schedule = criticalPath.schedule(input.graph(), calendar, calendar.projectOrigin(input.startDate()),
                    input.endDate());
            long open = input.tasks().stream().filter(task -> OPEN.contains(task.status())).count();
            long critical = schedule.criticalTaskIds().size();
            boolean taskOverrun = schedule.tasks().stream().anyMatch(task -> task.deadlineOverrun());
            Risk risk = schedule.projectDeadlineOverrun() ? Risk.OVERDUE
                    : taskOverrun ? Risk.AT_RISK : Risk.ON_TRACK;
            cards.add(new ProjectRisk(project.id(), project.name(), project.status().name(), project.timezone(),
                    project.endDate(), open, critical, schedule.durationMinutes(),
                    schedule.projectDeadlineOverrun(), taskOverrun, risk));
        }
        return new PortfolioPage(Instant.now(), cards, listed.page(), listed.size(), listed.totalElements(),
                listed.totalPages());
    }
}
