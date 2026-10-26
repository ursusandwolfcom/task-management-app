package com.example.taskmanagement.project.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public interface ProjectOperations {
    record ProjectPage(List<ProjectView> content, int page, int size, long totalElements, int totalPages) {
        public ProjectPage {
            content = List.copyOf(content);
        }
    }

    record MemberView(long userId, boolean owner) {
    }

    record MemberPage(List<MemberView> content, int page, int size, long totalElements, int totalPages) {
        public MemberPage {
            content = List.copyOf(content);
        }
    }

    ProjectView create(long actorId, @NotNull @Valid ProjectData data);

    ProjectView read(long actorId, long projectId);

    ProjectPage list(long actorId, int page, int size);

    ProjectView replace(long actorId, long projectId, @NotNull @Valid ProjectData data,
            @PositiveOrZero long version);

    ProjectView patch(long actorId, long projectId, @NotNull ProjectPatch patch, @PositiveOrZero long version);

    MemberPage members(long actorId, long projectId, int page, int size);

    ProjectView addMember(long actorId, long projectId, long userId, @PositiveOrZero long version);

    ProjectView removeMember(long actorId, long projectId, long userId, @PositiveOrZero long version);

    ProjectView transferOwner(long actorId, long projectId, long userId, @PositiveOrZero long version);

    /** Deletes a project without tasks; owned collaboration metadata is removed with its parent. */
    void deleteEmpty(long actorId, long projectId, @PositiveOrZero long version);
}
