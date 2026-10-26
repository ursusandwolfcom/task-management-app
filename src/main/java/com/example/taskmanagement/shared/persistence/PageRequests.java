package com.example.taskmanagement.shared.persistence;

import java.util.function.Supplier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Shared page bounds; each module retains its own validation error contract. */
public final class PageRequests {
    private static final int MAX_PAGE = 1000000;
    private static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest descending(int page, int size, String property,
            Supplier<? extends RuntimeException> invalid) {
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
            throw invalid.get();
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, property));
    }
}
