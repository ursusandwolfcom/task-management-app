package com.example.taskmanagement.task.domain;

/** Persisted lifecycle; readiness is calculated separately from execution facts. */
public enum TaskStatus {
    NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
}
