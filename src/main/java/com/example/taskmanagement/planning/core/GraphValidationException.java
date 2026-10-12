package com.example.taskmanagement.planning.core;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Typed graph diagnostic; HTTP status mapping belongs to the web adapter. */
public final class GraphValidationException extends IllegalArgumentException {

    public enum Reason {
        DUPLICATE_TASK, UNKNOWN_TASK, SELF_DEPENDENCY, DUPLICATE_DEPENDENCY,
        CYCLIC_DEPENDENCY, INVALID_EXECUTION_FACTS
    }

    private final Reason reason;
    private final List<Long> taskIds;

    public GraphValidationException(Reason reason, String message, Collection<Long> taskIds) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.taskIds = taskIds.stream().distinct().sorted().toList();
    }

    public Reason reason() {
        return reason;
    }

    /** For a cycle, these are unresolved vertices, including descendants, rather than a cycle witness. */
    public List<Long> taskIds() {
        return taskIds;
    }
}
