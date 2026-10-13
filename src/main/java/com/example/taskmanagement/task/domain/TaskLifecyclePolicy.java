package com.example.taskmanagement.task.domain;

import java.util.Objects;

/** Pure state transitions. Application use cases must additionally check access and resource guards. */
public final class TaskLifecyclePolicy {

    public enum Command {
        START, COMPLETE, FAIL, CANCEL, RETRY
    }

    private TaskLifecyclePolicy() {
    }

    public static TaskStatus nextStatus(TaskStatus current, Command command) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(command, "command");
        boolean allowed = switch (command) {
            case START -> current == TaskStatus.NOT_STARTED;
            case COMPLETE, FAIL -> current == TaskStatus.IN_PROGRESS;
            case CANCEL -> current == TaskStatus.NOT_STARTED || current == TaskStatus.IN_PROGRESS
                    || current == TaskStatus.FAILED;
            case RETRY -> current == TaskStatus.FAILED;
        };
        if (!allowed) {
            throw new IllegalStateException("Cannot " + command + " a task in " + current);
        }
        return switch (command) {
            case START -> TaskStatus.IN_PROGRESS;
            case COMPLETE -> TaskStatus.COMPLETED;
            case FAIL -> TaskStatus.FAILED;
            case CANCEL -> TaskStatus.CANCELLED;
            case RETRY -> TaskStatus.NOT_STARTED;
        };
    }
}
