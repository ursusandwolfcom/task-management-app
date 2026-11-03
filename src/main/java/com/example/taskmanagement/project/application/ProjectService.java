package com.example.taskmanagement.project.application;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.shared.persistence.PageRequests;
import com.example.taskmanagement.project.api.ProjectCoordination;
import com.example.taskmanagement.project.api.ProjectContentGuard;
import com.example.taskmanagement.project.api.ProjectData;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.project.api.ProjectPatch;
import com.example.taskmanagement.project.api.ProjectView;
import com.example.taskmanagement.project.persistence.ProjectEntity;
import com.example.taskmanagement.project.api.MemberBreaks;
import com.example.taskmanagement.project.api.MemberCapacity;
import com.example.taskmanagement.project.api.ProjectBreakWindow;
import com.example.taskmanagement.project.persistence.ProjectBreakRepository;
import com.example.taskmanagement.project.persistence.ProjectCapacityRepository;
import com.example.taskmanagement.project.persistence.ProjectHolidayRepository;
import com.example.taskmanagement.project.persistence.ProjectMemberBreakRepository;
import com.example.taskmanagement.project.persistence.ProjectMemberEntity;
import com.example.taskmanagement.project.persistence.ProjectMemberRepository;
import com.example.taskmanagement.project.persistence.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class ProjectService implements ProjectOperations, ProjectCoordination {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectHolidayRepository holidayRepository;
    private final ProjectBreakRepository breakRepository;
    private final ProjectCapacityRepository capacityRepository;
    private final ProjectMemberBreakRepository memberBreakRepository;
    private final IdentityOperations identity;
    private final EntityManager entityManager;
    private final Validator validator;
    private final List<ProjectContentGuard> contentGuards;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members,
            ProjectHolidayRepository holidayRepository, ProjectBreakRepository breakRepository,
            ProjectCapacityRepository capacityRepository, ProjectMemberBreakRepository memberBreakRepository,
            IdentityOperations identity, EntityManager entityManager, Validator validator,
            List<ProjectContentGuard> contentGuards) {
        this.projects = projects;
        this.members = members;
        this.holidayRepository = holidayRepository;
        this.breakRepository = breakRepository;
        this.capacityRepository = capacityRepository;
        this.memberBreakRepository = memberBreakRepository;
        this.identity = identity;
        this.entityManager = entityManager;
        this.validator = validator;
        this.contentGuards = List.copyOf(contentGuards);
    }

    @Override
    public ProjectView create(long actorId, ProjectData data) {
        identity.profile(actorId);
        validateData(data);
        var project = projects.saveAndFlush(new ProjectEntity(data, actorId));
        members.saveAndFlush(new ProjectMemberEntity(project.view().id(), actorId));
        return project.view();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectView read(long actorId, long projectId) {
        var project = found(projectId);
        authorize(actorId, project, Permission.READ);
        return project.view();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPage list(long actorId, int page, int size) {
        var role = identity.currentRole(actorId);
        var result = projects.accessible(actorId, role == UserRole.ADMIN, paging(page, size, "id"));
        return new ProjectPage(result.getContent().stream().map(ProjectEntity::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public ProjectView replace(long actorId, long projectId, ProjectData data, long version) {
        var project = managed(actorId, projectId, version);
        validateData(data);
        project.update(data);
        return projects.saveAndFlush(project).view();
    }

    @Override
    public ProjectView patch(long actorId, long projectId, ProjectPatch patch, long version) {
        var project = managed(actorId, projectId, version);
        var data = patch.applyTo(project.view());
        validateData(data);
        project.update(data);
        return projects.saveAndFlush(project).view();
    }

    @Override
    @Transactional(readOnly = true)
    public MemberPage members(long actorId, long projectId, int page, int size) {
        var project = found(projectId);
        authorize(actorId, project, Permission.READ);
        var result = members.findByProjectId(projectId, paging(page, size, "userId"));
        return new MemberPage(result.getContent().stream()
                .map(member -> new MemberView(member.userId(), member.userId() == project.view().ownerId())).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public ProjectView addMember(long actorId, long projectId, long userId, long version) {
        var project = managed(actorId, projectId, version);
        identity.profile(userId);
        if (members.existsByProjectIdAndUserId(projectId, userId)) {
            throw error(ProjectException.Reason.MEMBER_ALREADY_EXISTS);
        }
        members.saveAndFlush(new ProjectMemberEntity(projectId, userId));
        project.inputChanged();
        return projects.saveAndFlush(project).view();
    }

    @Override
    public ProjectView removeMember(long actorId, long projectId, long userId, long version) {
        var project = managed(actorId, projectId, version);
        if (project.view().ownerId() == userId) {
            throw error(ProjectException.Reason.OWNER_REMOVAL_FORBIDDEN);
        }
        var member = members.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> error(ProjectException.Reason.MEMBER_NOT_FOUND));
        contentGuards.forEach(guard -> guard.beforeRemoveMember(projectId, userId));
        members.delete(member);
        members.flush();
        project.inputChanged();
        return projects.saveAndFlush(project).view();
    }

    @Override
    public ProjectView transferOwner(long actorId, long projectId, long userId, long version) {
        var project = managed(actorId, projectId, version);
        if (!members.existsByProjectIdAndUserId(projectId, userId)) {
            throw error(ProjectException.Reason.MEMBER_NOT_FOUND);
        }
        // Both memberships remain; only owner rights move to the selected existing member.
        project.transferOwner(userId);
        return projects.saveAndFlush(project).view();
    }

    @Override
    public void deleteEmpty(long actorId, long projectId, long version) {
        var project = managed(actorId, projectId, version);
        contentGuards.forEach(guard -> guard.beforeDelete(projectId));
        projects.delete(project);
        projects.flush();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectView lockAndAuthorize(long actorId, long projectId, Permission permission) {
        var project = locked(projectId);
        authorize(actorId, project, permission);
        return project.view();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectView markInputChanged(long actorId, long projectId) {
        var project = locked(projectId);
        authorize(actorId, project, Permission.WRITE_TASKS);
        project.inputChanged();
        return projects.saveAndFlush(project).view();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isMember(long projectId, long userId) {
        return members.existsByProjectIdAndUserId(projectId, userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Set<LocalDate> holidays(long projectId) {
        return holidayRepository.list(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<LocalDate> holidays(long actorId, long projectId) {
        read(actorId, projectId);
        return holidayRepository.list(projectId);
    }

    @Override
    public ProjectView replaceHolidays(long actorId, long projectId, Set<LocalDate> dates, long version) {
        var project = managed(actorId, projectId, version);
        Set<LocalDate> next = dates == null ? Set.of() : Set.copyOf(dates);
        if (next.stream().anyMatch(day -> day.isBefore(project.view().startDate())
                || day.isAfter(project.view().endDate()))) {
            throw error(ProjectException.Reason.VALIDATION_ERROR);
        }
        holidayRepository.replace(projectId, new HashSet<>(next));
        project.calendarChanged();
        return projects.saveAndFlush(project).view();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ProjectBreakWindow> breaks(long projectId) {
        return effectiveBreaks(breakRepository.list(projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectBreakWindow> breaks(long actorId, long projectId) {
        read(actorId, projectId);
        return effectiveBreaks(breakRepository.list(projectId));
    }

    @Override
    public ProjectView replaceBreaks(long actorId, long projectId, List<ProjectBreakWindow> windows, long version) {
        var project = managed(actorId, projectId, version);
        List<ProjectBreakWindow> next;
        try {
            next = windows == null || windows.isEmpty()
                    ? List.of()
                    : ProjectBreakWindow.normalize(windows);
        } catch (IllegalArgumentException ex) {
            throw error(ProjectException.Reason.VALIDATION_ERROR);
        }
        breakRepository.replace(projectId, next);
        project.calendarChanged();
        return projects.saveAndFlush(project).view();
    }

    private static List<ProjectBreakWindow> effectiveBreaks(List<ProjectBreakWindow> stored) {
        return stored.isEmpty() ? ProjectBreakWindow.DEFAULT_BREAKS : List.copyOf(stored);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Integer> capacities(long projectId) {
        return capacityRepository.list(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberCapacity> capacities(long actorId, long projectId) {
        read(actorId, projectId);
        return capacityRepository.list(projectId).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MemberCapacity(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public ProjectView replaceCapacities(long actorId, long projectId, List<MemberCapacity> capacities, long version) {
        var project = managed(actorId, projectId, version);
        Map<Long, Integer> next = new HashMap<>();
        if (capacities != null) {
            for (MemberCapacity capacity : capacities) {
                if (capacity == null || capacity.dailyMinutes() < 1 || capacity.dailyMinutes() > 480
                        || !members.existsByProjectIdAndUserId(projectId, capacity.userId())) {
                    throw error(ProjectException.Reason.VALIDATION_ERROR);
                }
                if (next.put(capacity.userId(), capacity.dailyMinutes()) != null) {
                    throw error(ProjectException.Reason.VALIDATION_ERROR);
                }
            }
        }
        capacityRepository.replace(projectId, next);
        project.calendarChanged();
        return projects.saveAndFlush(project).view();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, List<ProjectBreakWindow>> memberBreaks(long projectId) {
        return memberBreakRepository.list(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberBreaks> memberBreaks(long actorId, long projectId) {
        read(actorId, projectId);
        return memberBreakRepository.list(projectId).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MemberBreaks(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public ProjectView replaceMemberBreaks(long actorId, long projectId, List<MemberBreaks> entries, long version) {
        var project = managed(actorId, projectId, version);
        List<MemberBreaks> next = new ArrayList<>();
        if (entries != null) {
            for (MemberBreaks entry : entries) {
                if (entry == null || entry.windows().isEmpty()
                        || !members.existsByProjectIdAndUserId(projectId, entry.userId())) {
                    throw error(ProjectException.Reason.VALIDATION_ERROR);
                }
                List<ProjectBreakWindow> windows;
                try {
                    windows = ProjectBreakWindow.normalize(entry.windows());
                } catch (IllegalArgumentException ex) {
                    throw error(ProjectException.Reason.VALIDATION_ERROR);
                }
                if (next.stream().anyMatch(existing -> existing.userId() == entry.userId())) {
                    throw error(ProjectException.Reason.VALIDATION_ERROR);
                }
                next.add(new MemberBreaks(entry.userId(), windows));
            }
        }
        memberBreakRepository.replace(projectId, next);
        project.calendarChanged();
        return projects.saveAndFlush(project).view();
    }

    private ProjectEntity managed(long actorId, long projectId, long version) {
        var project = locked(projectId);
        authorize(actorId, project, Permission.MANAGE_PROJECT);
        if (project.view().version() != version) {
            throw error(ProjectException.Reason.VERSION_CONFLICT);
        }
        return project;
    }

    private ProjectEntity locked(long id) {
        // A scalar lock does not compare a stale managed entity version before we can reload it.
        projects.lockId(id).orElseThrow(() -> error(ProjectException.Reason.PROJECT_NOT_FOUND));
        var project = found(id);
        entityManager.refresh(project);
        return project;
    }

    private ProjectEntity found(long id) {
        return projects.findById(id).orElseThrow(() -> error(ProjectException.Reason.PROJECT_NOT_FOUND));
    }

    private void authorize(long actorId, ProjectEntity project, Permission permission) {
        var role = identity.currentRole(actorId);
        if (role == UserRole.ADMIN || project.view().ownerId() == actorId) {
            return;
        }
        if (!members.existsByProjectIdAndUserId(project.view().id(), actorId)) {
            throw error(ProjectException.Reason.PROJECT_NOT_FOUND);
        }
        if (permission == Permission.MANAGE_PROJECT) {
            throw error(ProjectException.Reason.FORBIDDEN);
        }
    }

    private void validateData(ProjectData data) {
        if (!validator.validate(data).isEmpty()) {
            throw error(ProjectException.Reason.VALIDATION_ERROR);
        }
        data.validateDatesAndTimezone();
    }

    private static PageRequest paging(int page, int size, String property) {
        return PageRequests.descending(page, size, property, () -> error(ProjectException.Reason.VALIDATION_ERROR));
    }

    private static ProjectException error(ProjectException.Reason reason) {
        return new ProjectException(reason);
    }
}
