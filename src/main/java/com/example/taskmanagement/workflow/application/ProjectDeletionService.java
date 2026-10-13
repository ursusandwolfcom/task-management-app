package com.example.taskmanagement.workflow.application;

import com.example.taskmanagement.project.api.ProjectOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entry point reserved for later task/file coordination; currently only empty persisted projects exist. */
@Service
public class ProjectDeletionService {
    private final ProjectOperations projects;

    public ProjectDeletionService(ProjectOperations projects) {
        this.projects = projects;
    }

    @Transactional
    public void delete(long actorId, long projectId, long version) {
        projects.deleteEmpty(actorId, projectId, version);
    }
}
