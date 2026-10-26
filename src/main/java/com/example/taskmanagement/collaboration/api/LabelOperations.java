package com.example.taskmanagement.collaboration.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public interface LabelOperations {
    record LabelPage(List<LabelView> content, int page, int size, long totalElements, int totalPages) {
        public LabelPage {
            content = List.copyOf(content);
        }
    }

    LabelView create(long actorId, long projectId, @NotNull @Valid LabelData data);

    LabelView read(long actorId, long labelId);

    LabelPage list(long actorId, long projectId, int page, int size);

    LabelView replace(long actorId, long labelId, @NotNull @Valid LabelData data, @PositiveOrZero long version);

    void delete(long actorId, long labelId, @PositiveOrZero long version);

    void link(long actorId, long taskId, long labelId);

    void unlink(long actorId, long taskId, long labelId);

    LabelPage taskLabels(long actorId, long taskId, int page, int size);
}
