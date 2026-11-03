package com.example.taskmanagement.task.application;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.shared.persistence.PageRequests;
import com.example.taskmanagement.task.api.ActivityEvents;
import com.example.taskmanagement.task.api.ActivityOperations;
import com.example.taskmanagement.task.api.TaskException;
import com.example.taskmanagement.task.api.TimeEntryData;
import com.example.taskmanagement.task.api.TimeEntryOperations;
import com.example.taskmanagement.task.api.TimeEntryView;
import com.example.taskmanagement.task.persistence.TimeEntryEntity;
import com.example.taskmanagement.task.persistence.TimeEntryRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class TimeEntryService implements TimeEntryOperations {
    private final TimeEntryRepository entries;
    private final TaskProjectAccess access;
    private final IdentityOperations identity;
    private final ActivityOperations activity;
    private final Clock clock;

    public TimeEntryService(TimeEntryRepository entries, TaskProjectAccess access, IdentityOperations identity,
            ActivityOperations activity, Clock clock) {
        this.entries = entries;
        this.access = access;
        this.identity = identity;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    public TimeEntryView create(long actorId, TimeEntryData data) {
        var project = access.lock(actorId, data.taskId());
        String note = data.note() == null || data.note().isBlank() ? null : data.note().strip();
        var created = entries.saveAndFlush(new TimeEntryEntity(project.id(), data.taskId(), actorId, data.workDate(),
                data.minutes(), note, clock.instant().truncatedTo(ChronoUnit.MICROS))).view();
        activity.record(actorId, project.id(), data.taskId(), ActivityEvents.TIME_LOGGED,
                "Учёт времени: " + data.minutes() + " мин");
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public TimeEntryPage list(long actorId, long taskId, int page, int size) {
        access.readable(actorId, taskId);
        var paging = PageRequests.descending(page, size, "id",
                () -> new TaskException(TaskException.Reason.VALIDATION_ERROR));
        var result = entries.findByTaskId(taskId, paging);
        return new TimeEntryPage(result.getContent().stream().map(TimeEntryEntity::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(),
                entries.sumMinutesByTaskId(taskId));
    }

    @Override
    public void delete(long actorId, long entryId) {
        var existing = entries.findById(entryId)
                .orElseThrow(() -> new TaskException(TaskException.Reason.TIME_ENTRY_NOT_FOUND));
        var project = access.lock(actorId, existing.view().taskId());
        var entry = entries.findById(entryId)
                .orElseThrow(() -> new TaskException(TaskException.Reason.TIME_ENTRY_NOT_FOUND));
        if (actorId != entry.view().userId() && actorId != project.ownerId()
                && identity.currentRole(actorId) != UserRole.ADMIN) {
            throw new TaskException(TaskException.Reason.FORBIDDEN);
        }
        activity.record(actorId, project.id(), entry.view().taskId(), ActivityEvents.TIME_ENTRY_DELETED,
                "Удалена запись времени");
        entries.delete(entry);
        entries.flush();
    }
}
