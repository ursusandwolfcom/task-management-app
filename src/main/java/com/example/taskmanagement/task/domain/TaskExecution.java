package com.example.taskmanagement.task.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable execution state. Database locking and authoritative readiness belong to application use cases. */
public final class TaskExecution {

    private final TaskStatus status;
    private final List<ExecutionAttempt> attempts;

    private TaskExecution(TaskStatus status, List<ExecutionAttempt> attempts) {
        this.status = status;
        this.attempts = List.copyOf(attempts);
    }

    public static TaskExecution notStarted() {
        return new TaskExecution(TaskStatus.NOT_STARTED, List.of());
    }

    /** Restores persisted facts, rejecting histories that could not arise through lifecycle commands. */
    public static TaskExecution restore(TaskStatus status, List<ExecutionAttempt> attempts) {
        Objects.requireNonNull(status, "status");
        var history = List.copyOf(attempts);
        for (int index = 0; index < history.size(); index++) {
            var attempt = history.get(index);
            if (attempt.number() != index + 1) {
                throw new IllegalArgumentException("Attempt numbers must be consecutive");
            }
            if (index > 0) {
                var previous = history.get(index - 1);
                if (previous.outcome() != TaskStatus.FAILED || attempt.actualStart().isBefore(previous.actualEnd())) {
                    throw new IllegalArgumentException("Only a failed attempt can be retried");
                }
            }
        }
        if (history.isEmpty()) {
            if (status != TaskStatus.NOT_STARTED && status != TaskStatus.CANCELLED) {
                throw new IllegalArgumentException("Execution status requires an attempt");
            }
        } else {
            var outcome = history.getLast().outcome();
            boolean valid = switch (status) {
                case NOT_STARTED -> outcome == TaskStatus.FAILED;
                case IN_PROGRESS -> outcome == null;
                case COMPLETED -> outcome == TaskStatus.COMPLETED;
                case FAILED -> outcome == TaskStatus.FAILED;
                case CANCELLED -> outcome == TaskStatus.CANCELLED || outcome == TaskStatus.FAILED;
            };
            if (!valid) {
                throw new IllegalArgumentException("Status and last attempt disagree");
            }
        }
        return new TaskExecution(status, history);
    }

    public TaskStatus status() {
        return status;
    }

    public List<ExecutionAttempt> attempts() {
        return attempts;
    }

    /** The caller supplies readiness from actual predecessor completion, checked under its transaction lock. */
    public TaskExecution start(Instant at, boolean predecessorsCompleted) {
        var next = TaskLifecyclePolicy.nextStatus(status, TaskLifecyclePolicy.Command.START);
        Objects.requireNonNull(at, "at");
        if (!predecessorsCompleted) {
            throw new IllegalStateException("All predecessors must be completed before start");
        }
        if (!attempts.isEmpty() && at.isBefore(attempts.getLast().actualEnd())) {
            throw new IllegalArgumentException("A new attempt cannot precede the previous attempt end");
        }
        var history = new ArrayList<>(attempts);
        history.add(new ExecutionAttempt(Math.addExact(history.size(), 1), at, null, null));
        return new TaskExecution(next, history);
    }

    public TaskExecution complete(Instant at) {
        return finish(TaskLifecyclePolicy.Command.COMPLETE, at);
    }

    public TaskExecution fail(Instant at) {
        return finish(TaskLifecyclePolicy.Command.FAIL, at);
    }

    public TaskExecution cancel(Instant at) {
        return finish(TaskLifecyclePolicy.Command.CANCEL, at);
    }

    /** Retains the failed attempt; the next actual start allocates a new attempt number. */
    public TaskExecution retry() {
        return new TaskExecution(TaskLifecyclePolicy.nextStatus(status, TaskLifecyclePolicy.Command.RETRY), attempts);
    }

    private TaskExecution finish(TaskLifecyclePolicy.Command command, Instant at) {
        var next = TaskLifecyclePolicy.nextStatus(status, command);
        Objects.requireNonNull(at, "at");
        if (!attempts.isEmpty() && at.isBefore(attempts.getLast().actualStart())) {
            throw new IllegalArgumentException("Command time cannot precede the last attempt start");
        }
        if (status != TaskStatus.IN_PROGRESS) {
            if (!attempts.isEmpty() && at.isBefore(attempts.getLast().actualEnd())) {
                throw new IllegalArgumentException("Command time cannot precede the last attempt end");
            }
            return new TaskExecution(next, attempts);
        }
        var history = new ArrayList<>(attempts);
        history.set(history.size() - 1, history.getLast().finish(at, next));
        return new TaskExecution(next, history);
    }
}
