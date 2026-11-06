package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.example.taskmanagement.dto.ProjectRequest;
import com.example.taskmanagement.service.ProjectService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
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
                awaitStart(started);
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

    @Test
    void removedMemberCannotDeleteAttachmentAfterWaitingForProjectLock() throws Exception {
        var owner = account();
        var member = account();
        long project = project(owner, member);
        long task = task(owner, project, null);
        when(dropbox.upload(eq("race.txt"), any())).thenReturn("id:revocation-race");
        var file = new MockMultipartFile("file", "race.txt", "text/plain", new byte[] {1});
        var upload = mvc.perform(multipart("/api/attachments").file(file).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(upload.getStatus()).isEqualTo(201);
        long id = mapper.readTree(upload.getContentAsString()).get("id").asLong();
        clearInvocations(dropbox);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = transactions.execute(status -> {
                projects.lock(owner.id(), project, true);
                var started = new CountDownLatch(1);
                var deletion = executor.submit(() -> {
                    started.countDown();
                    return call("DELETE", "/api/attachments/" + id, member.token(), null);
                });
                awaitStart(started);
                assertThatThrownBy(() -> deletion.get(500, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                projects.update(owner.id(), project, mapper.convertValue(projectBody(List.of()), ProjectRequest.class));
                return deletion;
            });
            assertThat(pending).isNotNull();
            var result = pending.get(10, TimeUnit.SECONDS);
            verifyNoInteractions(dropbox);
            assertThat(result.getStatus()).as(result.getContentAsString()).isEqualTo(404);
            assertThat(json(200, "GET", "/api/attachments?taskId=" + task, owner.token(), null)
                    .get("content").size()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitStart(CountDownLatch started) {
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
