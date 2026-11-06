package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class AttachmentTest extends ApiTestSupport {
    @Test
    void uploadListDownloadAndDeleteUsePersistedDropboxId() throws Exception {
        var owner = account();
        long task = task(owner, project(owner), null);
        when(dropbox.upload(eq("notes.txt"), any())).thenReturn("id:course-file");
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        var response = mvc.perform(multipart("/api/attachments").file(file).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        long id = mapper.readTree(response.getContentAsString()).get("id").asLong();
        var attachment = json(200, "GET", "/api/attachments?taskId=" + task, owner.token(), null)
                .get("content").get(0);
        assertThat(attachment.get("dropboxFileId").asText()).isEqualTo("id:course-file");
        assertThat(attachment.get("uploadDate").asText()).isEqualTo("2030-02-10T09:00:00");
        when(dropbox.download("id:course-file")).thenReturn(file.getBytes());
        var download = call("GET", "/api/attachments/" + id + "/download", owner.token(), null);
        assertThat(download.getStatus()).isEqualTo(200);
        assertThat(download.getContentAsByteArray()).isEqualTo(file.getBytes());
        assertThat(download.getHeader("Content-Disposition")).contains("attachment", "notes.txt");
        assertThat(call("DELETE", "/api/tasks/" + task, owner.token(), null).getStatus()).isEqualTo(409);
        assertThat(call("DELETE", "/api/attachments/" + id, owner.token(), null).getStatus()).isEqualTo(204);
        verify(dropbox).delete("id:course-file");
        assertThat(call("GET", "/api/attachments/" + id + "/download", owner.token(), null)
                .getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/api/tasks/" + task, owner.token(), null).getStatus()).isEqualTo(204);
    }

    @Test
    void providerFailurePreservesMetadataAndOutsidersCannotAccessFile() throws Exception {
        var owner = account();
        var outsider = account();
        long task = task(owner, project(owner), null);
        when(dropbox.upload(eq("safe.txt"), any())).thenReturn("id:failure-test");
        var file = new MockMultipartFile("file", "safe.txt", "text/plain", new byte[] {1, 2});
        var response = mvc.perform(multipart("/api/attachments").file(file).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        long id = mapper.readTree(response.getContentAsString()).get("id").asLong();
        assertThat(call("GET", "/api/attachments/" + id + "/download", outsider.token(), null)
                .getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/api/attachments/" + id, outsider.token(), null).getStatus()).isEqualTo(404);
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dropbox operation failed"))
                .when(dropbox).delete("id:failure-test");
        assertThat(call("DELETE", "/api/attachments/" + id, owner.token(), null).getStatus()).isEqualTo(502);
        assertThat(json(200, "GET", "/api/attachments?taskId=" + task, owner.token(), null)
                .get("content").size()).isEqualTo(1);
        when(dropbox.download("id:failure-test")).thenThrow(new java.io.IOException("sensitive provider detail"));
        var failed = call("GET", "/api/attachments/" + id + "/download", owner.token(), null);
        assertThat(failed.getStatus()).isEqualTo(502);
        assertThat(failed.getContentAsString()).doesNotContain("sensitive provider detail");
    }

    @Test
    void unsafeNamesEmptyFilesAndUnauthorizedUploadsAreRejectedBeforeDropbox() throws Exception {
        var owner = account();
        var outsider = account();
        long task = task(owner, project(owner), null);
        for (String name : new String[] {"../secret.txt", "..", "bad\\name.txt", "bad\nname.txt",
                "bad\u007fname.txt", "bad\u0085name.txt", ""}) {
            var file = new MockMultipartFile("file", name, "text/plain", new byte[] {1});
            var result = mvc.perform(multipart("/api/attachments").file(file).param("taskId", "" + task)
                    .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse();
            assertThat(result.getStatus()).as(name).isEqualTo(400);
        }
        var empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThat(mvc.perform(multipart("/api/attachments").file(empty).param("taskId", "" + task)
                .header("Authorization", "Bearer " + owner.token())).andReturn().getResponse().getStatus())
                .isEqualTo(400);
        assertThat(mvc.perform(multipart("/api/attachments").file(empty).param("taskId", "" + task)
                .header("Authorization", "Bearer " + outsider.token())).andReturn().getResponse().getStatus())
                .isEqualTo(404);
        org.mockito.Mockito.verifyNoInteractions(dropbox);
    }
}
