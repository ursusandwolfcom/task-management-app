package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.DropboxFileClient;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AttachmentStorageConfiguration {
    @Bean
    @ConditionalOnProperty(name = "attachment.storage", havingValue = "local", matchIfMissing = true)
    AttachmentObjectStore localAttachmentObjectStore(@Value("${attachment.storage-dir}") String storageDir)
            throws IOException {
        return new LocalAttachmentObjectStore(storageDir);
    }

    @Bean
    @ConditionalOnProperty(name = "attachment.storage", havingValue = "dropbox")
    DropboxFileClient dropboxFileClient(@Value("${dropbox.access-token:}") String accessToken) {
        String token = accessToken == null ? "" : accessToken.strip();
        if (token.isEmpty()) {
            throw new IllegalStateException("attachment.storage=dropbox requires DROPBOX_ACCESS_TOKEN");
        }
        return new SdkDropboxFileClient(token);
    }

    @Bean
    @ConditionalOnProperty(name = "attachment.storage", havingValue = "dropbox")
    AttachmentObjectStore dropboxAttachmentObjectStore(DropboxFileClient client,
            @Value("${dropbox.app-folder:/projectflow}") String appFolder) {
        return new DropboxAttachmentObjectStore(client, appFolder);
    }
}
