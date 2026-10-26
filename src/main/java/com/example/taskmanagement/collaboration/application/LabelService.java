package com.example.taskmanagement.collaboration.application;

import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.collaboration.api.CollaborationException.Reason;
import com.example.taskmanagement.collaboration.api.LabelData;
import com.example.taskmanagement.collaboration.api.LabelOperations;
import com.example.taskmanagement.collaboration.api.LabelView;
import com.example.taskmanagement.collaboration.persistence.LabelEntity;
import com.example.taskmanagement.collaboration.persistence.LabelRepository;
import com.example.taskmanagement.collaboration.persistence.TaskLabelRepository;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.project.api.ProjectOperations;
import com.example.taskmanagement.shared.persistence.PageRequests;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class LabelService implements LabelOperations {
    private final LabelRepository labels;
    private final TaskLabelRepository links;
    private final CollaborationAccess access;
    private final ProjectOperations projects;
    private final EntityManager entityManager;

    public LabelService(LabelRepository labels, TaskLabelRepository links, CollaborationAccess access,
            ProjectOperations projects, EntityManager entityManager) {
        this.labels = labels;
        this.links = links;
        this.access = access;
        this.projects = projects;
        this.entityManager = entityManager;
    }

    @Override
    public LabelView create(long actorId, long projectId, LabelData data) {
        access.lockProject(actorId, projectId, Reason.LABEL_NOT_FOUND);
        uniqueName(projectId, data, 0);
        return labels.saveAndFlush(new LabelEntity(projectId, data)).view();
    }

    @Override
    @Transactional(readOnly = true)
    public LabelView read(long actorId, long labelId) {
        authorize(actorId, labelId);
        return entity(labelId).view();
    }

    @Override
    @Transactional(readOnly = true)
    public LabelPage list(long actorId, long projectId, int page, int size) {
        projects.read(actorId, projectId);
        return result(labels.findByProjectId(projectId, paging(page, size)).map(LabelEntity::view), page, size);
    }

    @Override
    public LabelView replace(long actorId, long labelId, LabelData data, long version) {
        var label = locked(actorId, labelId);
        checkVersion(label, version);
        uniqueName(label.view().projectId(), data, labelId);
        label.replace(data);
        labels.flush();
        return label.view();
    }

    @Override
    public void delete(long actorId, long labelId, long version) {
        var label = locked(actorId, labelId);
        checkVersion(label, version);
        labels.delete(label);
        labels.flush();
    }

    @Override
    public void link(long actorId, long taskId, long labelId) {
        long projectId = access.lockTask(actorId, taskId).id();
        sameProject(actorId, projectId, labelId);
        links.link(projectId, taskId, labelId);
    }

    @Override
    public void unlink(long actorId, long taskId, long labelId) {
        long projectId = access.lockTask(actorId, taskId).id();
        sameProject(actorId, projectId, labelId);
        links.unlink(taskId, labelId);
    }

    @Override
    @Transactional(readOnly = true)
    public LabelPage taskLabels(long actorId, long taskId, int page, int size) {
        access.task(actorId, taskId);
        return result(links.list(taskId, paging(page, size)), page, size);
    }

    private LabelEntity locked(long actorId, long labelId) {
        long projectId = projectId(labelId);
        access.lockProject(actorId, projectId, Reason.LABEL_NOT_FOUND);
        projectId(labelId); // Fresh scalar existence check, even when the entity is already cached.
        var label = entity(labelId);
        entityManager.refresh(label);
        return label;
    }

    private void sameProject(long actorId, long projectId, long labelId) {
        long labelProjectId = projectId(labelId);
        if (labelProjectId != projectId) {
            authorize(actorId, labelId);
            throw error(Reason.CROSS_PROJECT_LABEL);
        }
    }

    private void authorize(long actorId, long labelId) {
        try {
            projects.read(actorId, projectId(labelId));
        } catch (ProjectException exception) {
            if (exception.reason() == ProjectException.Reason.PROJECT_NOT_FOUND) {
                throw error(Reason.LABEL_NOT_FOUND);
            }
            throw exception;
        }
    }

    private long projectId(long labelId) {
        return labels.projectId(labelId).orElseThrow(() -> error(Reason.LABEL_NOT_FOUND));
    }

    private LabelEntity entity(long labelId) {
        return labels.findById(labelId).orElseThrow(() -> error(Reason.LABEL_NOT_FOUND));
    }

    private void uniqueName(long projectId, LabelData data, long excludedId) {
        if (labels.nameExists(projectId, data.nameKey(), excludedId)) {
            throw error(Reason.LABEL_ALREADY_EXISTS);
        }
    }

    private static void checkVersion(LabelEntity label, long version) {
        if (label.view().version() != version) {
            throw error(Reason.VERSION_CONFLICT);
        }
    }

    private static Pageable paging(int page, int size) {
        return PageRequests.descending(page, size, "id", () -> error(Reason.VALIDATION_ERROR));
    }

    private static LabelPage result(Page<LabelView> result, int page, int size) {
        return new LabelPage(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    private static CollaborationException error(Reason reason) {
        return new CollaborationException(reason);
    }
}
