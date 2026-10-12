package com.example.taskmanagement.planning.core;

/** Finish-to-start direction: predecessor must complete before successor can start. */
public record DependencyEdge(long predecessorId, long successorId) {

    public DependencyEdge {
        if (predecessorId <= 0 || successorId <= 0) {
            throw new IllegalArgumentException("Dependency endpoint IDs must be positive");
        }
    }
}
