package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.DropboxFileClient;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class AttachmentStorageConfiguration {
    @Bean
    LocalAttachmentObjectStore localAttachmentObjectStore(@Value("${attachment.storage-dir}") String storageDir)
            throws IOException {
        return new LocalAttachmentObjectStore(storageDir);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "attachment.storage", havingValue = "local", matchIfMissing = true)
    AttachmentObjectStore localPrimaryAttachmentObjectStore(LocalAttachmentObjectStore local) {
        return local;
    }

    @Bean
    @ConditionalOnProperty(name = "attachment.storage", havingValue = "dropbox")
    DropboxFileClient dropboxFileClient(@Value("${dropbox.access-token:}") String accessToken,
            @Value("${dropbox.refresh-token:}") String refreshToken, @Value("${dropbox.app-key:}") String appKey,
            @Value("${dropbox.app-secret:}") String appSecret) {
        return new SdkDropboxFileClient(accessToken, refreshToken, appKey, appSecret);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "attachment.storage", havingValue = "dropbox")
    AttachmentObjectStore dropboxPrimaryAttachmentObjectStore(DropboxFileClient client,
            LocalAttachmentObjectStore local, @Value("${dropbox.app-folder:/projectflow}") String appFolder) {
        return new DualAttachmentObjectStore(new DropboxAttachmentObjectStore(client, appFolder), local);
    }
}
