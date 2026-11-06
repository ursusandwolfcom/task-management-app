package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.taskmanagement.dto.ProjectRequest;
import com.example.taskmanagement.service.ProjectService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ConcurrencyTest extends ApiTestSupport {
    @Autowired
    private ProjectService projects;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void concurrentAssignmentRechecksMembershipAfterRemovalCommits() throws Exception {
        var owner = account();
        var member = account();
        long project = project(owner, member);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = transactions.execute(status -> {
                projects.lock(owner.id(), project, true);
                var started = new CountDownLatch(1);
                var assignment = executor.submit(() -> {
                    started.countDown();
                    return call("POST", "/api/tasks", owner.token(), taskBody(project, member.id(), List.of()));
                });
                assertThatCodeAwait(started);
                projects.update(owner.id(), project, mapper.convertValue(projectBody(List.of()), ProjectRequest.class));
                assertThatThrownBy(() -> assignment.get(250, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                return assignment;
            });
            assertThat(pending).isNotNull();
            assertThat(pending.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(409);
            assertThat(json(200, "GET", "/api/tasks?projectId=" + project, owner.token(), null)
                    .get("content").size()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertThatCodeAwait(CountDownLatch started) {
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
