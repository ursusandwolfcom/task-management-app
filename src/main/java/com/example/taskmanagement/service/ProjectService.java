package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.ProjectRequest;
import com.example.taskmanagement.dto.ProjectResponse;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Project;
import com.example.taskmanagement.model.Role;
import com.example.taskmanagement.model.User;
import com.example.taskmanagement.repository.ProjectRepository;
import com.example.taskmanagement.repository.TaskRepository;
import java.util.HashSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProjectService {
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final UserService users;
    private final ResponseMapper mapper;

    public ProjectService(ProjectRepository projects, TaskRepository tasks,
            UserService users, ResponseMapper mapper) {
        this.projects = projects;
        this.tasks = tasks;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Project accessible(Long actorId, Long id, boolean ownerOnly) {
        User actor = users.getUser(actorId);
        Project project = projects.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        boolean owner = project.getOwner().getId().equals(actorId);
        boolean member = project.getMembers().stream().anyMatch(user -> user.getId().equals(actorId));
        if (actor.getRole() != Role.ADMIN && !owner && !member) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        if (ownerOnly && !owner && actor.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project owner required");
        }
        return project;
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(Long actorId, Pageable pageable) {
        return (users.getUser(actorId).getRole() == Role.ADMIN
                ? projects.findAll(pageable) : projects.findAccessible(actorId, pageable)).map(mapper::toResponse);
    }

    public Project lock(Long actorId, Long id, boolean ownerOnly) {
        projects.findForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        return accessible(actorId, id, ownerOnly);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long actorId, Long id) {
        return mapper.toResponse(accessible(actorId, id, false));
    }

    public ProjectResponse create(Long actorId, ProjectRequest request) {
        var project = new Project();
        project.setOwner(users.getUser(actorId));
        apply(project, request);
        return mapper.toResponse(projects.saveAndFlush(project));
    }

    public ProjectResponse update(Long actorId, Long id, ProjectRequest request) {
        Project project = lock(actorId, id, true);
        for (User member : project.getMembers()) {
            if (!request.memberIds().contains(member.getId())
                    && !project.getOwner().getId().equals(member.getId())
                    && tasks.existsByProjectIdAndAssigneeId(id, member.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reassign tasks before removing a member");
            }
        }
        apply(project, request);
        return mapper.toResponse(projects.saveAndFlush(project));
    }

    public void delete(Long actorId, Long id) {
        Project project = lock(actorId, id, true);
        if (tasks.existsByProjectId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delete project tasks first");
        }
        projects.delete(project);
    }

    private void apply(Project project, ProjectRequest request) {
        if (request.startDate() != null && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date precedes start date");
        }
        var members = new HashSet<User>();
        for (Long id : request.memberIds()) {
            members.add(users.getUser(id));
        }
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setStatus(request.status());
        project.setMembers(members);
    }
}
