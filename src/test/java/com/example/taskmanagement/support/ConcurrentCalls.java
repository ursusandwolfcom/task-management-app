package com.example.taskmanagement.support;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Collect only explicitly expected business failures; unexpected infrastructure errors still fail the test. */
public final class ConcurrentCalls {
    private ConcurrentCalls() {
    }

    public static List<Object> race(long timeoutSeconds, List<Class<? extends RuntimeException>> expected,
            Callable<Object> first, Callable<Object> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(capture(first, expected));
            var two = executor.submit(capture(second, expected));
            return List.of(one.get(timeoutSeconds, TimeUnit.SECONDS), two.get(timeoutSeconds, TimeUnit.SECONDS));
        }
    }

    public static Callable<Object> capture(Callable<Object> action, List<Class<? extends RuntimeException>> expected) {
        return () -> {
            try {
                return action.call();
            } catch (RuntimeException exception) {
                if (expected.stream().noneMatch(type -> type.isInstance(exception))) {
                    throw exception;
                }
                return exception;
            }
        };
    }

    public static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Coordination test timed out.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
