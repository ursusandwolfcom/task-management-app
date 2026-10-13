package com.example.taskmanagement.task.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskExecutionTest {

    private static final Instant START = Instant.parse("2026-10-13T08:00:00Z");
    private static final Instant END = START.plusSeconds(60);

    @Test
    void transitionMatrixMatchesConfirmedContract() {
        var allowed = Map.of(
                TaskStatus.NOT_STARTED, Map.of(TaskLifecyclePolicy.Command.START, TaskStatus.IN_PROGRESS,
                        TaskLifecyclePolicy.Command.CANCEL, TaskStatus.CANCELLED),
                TaskStatus.IN_PROGRESS, Map.of(TaskLifecyclePolicy.Command.COMPLETE, TaskStatus.COMPLETED,
                        TaskLifecyclePolicy.Command.FAIL, TaskStatus.FAILED,
                        TaskLifecyclePolicy.Command.CANCEL, TaskStatus.CANCELLED),
                TaskStatus.FAILED, Map.of(TaskLifecyclePolicy.Command.RETRY, TaskStatus.NOT_STARTED,
                        TaskLifecyclePolicy.Command.CANCEL, TaskStatus.CANCELLED));
        for (var state : TaskStatus.values()) {
            for (var command : TaskLifecyclePolicy.Command.values()) {
                var expected = allowed.getOrDefault(state, Map.of()).get(command);
                if (expected == null) {
                    assertThrows(IllegalStateException.class, () -> TaskLifecyclePolicy.nextStatus(state, command),
                            state + " / " + command);
                } else {
                    assertEquals(expected, TaskLifecyclePolicy.nextStatus(state, command), state + " / " + command);
                }
            }
        }
    }

    @Test
    void completionRecordsActualDatesWithoutChangingPreviousState() {
        var initial = TaskExecution.notStarted();
        var running = initial.start(START, true);
        var completed = running.complete(END);
        assertEquals(TaskStatus.COMPLETED, completed.status());
        assertEquals(List.of(new ExecutionAttempt(1, START, END, TaskStatus.COMPLETED)), completed.attempts());
        assertEquals(TaskStatus.NOT_STARTED, initial.status());
        assertTrue(initial.attempts().isEmpty());
        assertNull(running.attempts().getFirst().actualEnd());
    }

    @Test
    void retryPreservesFailedAttemptAndNextStartCreatesAnother() {
        var failed = TaskExecution.notStarted().start(START, true).fail(END);
        var retried = failed.retry();
        var running = retried.start(END.plusSeconds(1), true);
        var completed = running.complete(END.plusSeconds(2));
        assertEquals(TaskStatus.NOT_STARTED, retried.status());
        assertEquals(failed.attempts(), retried.attempts());
        assertEquals(failed.attempts().getFirst(), completed.attempts().getFirst());
        assertEquals(new ExecutionAttempt(2, END.plusSeconds(1), END.plusSeconds(2), TaskStatus.COMPLETED),
                completed.attempts().getLast());
        assertThrows(UnsupportedOperationException.class, () -> completed.attempts().clear());
    }

    @Test
    void blockedStartAndRepeatedStartAreRejected() {
        var initial = TaskExecution.notStarted();
        assertThrows(IllegalStateException.class, () -> initial.start(START, false));
        var running = initial.start(START, true);
        assertThrows(IllegalStateException.class, () -> running.start(END, true));
        assertThrows(IllegalStateException.class, running::retry);
        assertTrue(initial.attempts().isEmpty());
    }

    @Test
    void cancellationFromAllThreeAllowedStatesPreservesActualHistory() {
        var cancelledBeforeStart = TaskExecution.notStarted().cancel(START);
        assertEquals(TaskStatus.CANCELLED, cancelledBeforeStart.status());
        assertTrue(cancelledBeforeStart.attempts().isEmpty());
        var cancelledRunning = TaskExecution.notStarted().start(START, true).cancel(END);
        assertEquals(TaskStatus.CANCELLED, cancelledRunning.attempts().getFirst().outcome());
        var failed = TaskExecution.notStarted().start(START, true).fail(END);
        var cancelledFailed = failed.cancel(END.plusSeconds(1));
        assertEquals(TaskStatus.CANCELLED, cancelledFailed.status());
        assertEquals(failed.attempts(), cancelledFailed.attempts());
        assertEquals(TaskStatus.FAILED, cancelledFailed.attempts().getFirst().outcome());
        assertThrows(IllegalStateException.class, cancelledFailed::retry);
    }

    @Test
    void commandTimesCannotReverseExecutionHistory() {
        var running = TaskExecution.notStarted().start(START, true);
        assertThrows(IllegalArgumentException.class, () -> running.complete(START.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> running.fail(START.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> running.cancel(START.minusSeconds(1)));
        var failed = running.fail(END);
        assertThrows(IllegalArgumentException.class, () -> failed.cancel(START));
        assertThrows(IllegalArgumentException.class, () -> failed.retry().start(START, true));
        assertEquals(END, running.complete(END).attempts().getFirst().actualEnd());
    }

    @Test
    void attemptRejectsIncompleteOrInvalidOutcomeAndCannotBeRewritten() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionAttempt(0, START, null, null));
        assertThrows(NullPointerException.class, () -> new ExecutionAttempt(1, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionAttempt(1, START, END, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionAttempt(1, START, null, TaskStatus.FAILED));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionAttempt(1, START, END, TaskStatus.NOT_STARTED));
        var finished = new ExecutionAttempt(1, START, null, null).finish(END, TaskStatus.FAILED);
        assertThrows(IllegalStateException.class, () -> finished.finish(END, TaskStatus.COMPLETED));
    }

    @Test
    void repeatedRetriesKeepEveryAttemptAndCancellationWhileWaitingAddsNoFakeAttempt() {
        var execution = TaskExecution.notStarted();
        for (int number = 1; number <= 5; number++) {
            Instant start = START.plusSeconds(number * 10L);
            execution = execution.start(start, true).fail(start.plusSeconds(1)).retry();
            assertEquals(number, execution.attempts().size());
            assertEquals(new ExecutionAttempt(number, start, start.plusSeconds(1), TaskStatus.FAILED),
                    execution.attempts().getLast());
        }
        var cancelled = execution.cancel(START.plusSeconds(60));
        assertEquals(TaskStatus.CANCELLED, cancelled.status());
        assertEquals(execution.attempts(), cancelled.attempts());
        assertThrows(IllegalStateException.class, () -> cancelled.start(END, true));
        assertThrows(IllegalStateException.class, cancelled::retry);
        assertEquals(TaskStatus.NOT_STARTED, execution.status());
    }
}
