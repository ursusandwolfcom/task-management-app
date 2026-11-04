package com.example.taskmanagement.attachment.application;

import com.example.taskmanagement.attachment.api.DropboxFileClient;
import com.example.taskmanagement.attachment.api.DropboxOAuthClient;
import com.example.taskmanagement.attachment.api.MegaClient;
import com.example.taskmanagement.identity.api.DropboxAccountOperations;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

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
            LocalAttachmentObjectStore local, @Value("${dropbox.mode:app}") String mode,
            @Value("${dropbox.app-folder:/projectflow}") String appFolder,
            @Value("${dropbox.team-folder:}") String teamFolder) {
        String root = DropboxRootFolder.resolve(mode, appFolder, teamFolder);
        return new DualAttachmentObjectStore(new DropboxAttachmentObjectStore(client, root), local);
    }

    @Bean
    @ConditionalOnMissingBean(DropboxOAuthClient.class)
    DropboxOAuthClient dropboxOAuthClient(@Value("${dropbox.app-key:}") String appKey,
            @Value("${dropbox.app-secret:}") String appSecret, ObjectMapper json) {
        return new HttpDropboxOAuthClient(appKey, appSecret, json);
    }

    @Bean
    AttachmentObjectStoreFactory attachmentObjectStoreFactory(AttachmentObjectStore primary,
            DropboxAccountOperations accounts, MegaAccountService megaAccounts, MegaClient mega,
            @Value("${attachment.storage:local}") String storageMode,
            @Value("${dropbox.app-key:}") String appKey, @Value("${dropbox.app-secret:}") String appSecret,
            @Value("${dropbox.mode:app}") String mode,
            @Value("${dropbox.app-folder:/projectflow}") String appFolder,
            @Value("${dropbox.team-folder:}") String teamFolder) {
        String root = "dropbox".equalsIgnoreCase(storageMode == null ? "local" : storageMode.strip())
                ? DropboxRootFolder.resolve(mode, appFolder, teamFolder)
                : DropboxRootFolder.normalize(appFolder);
        var legacy = new DefaultAttachmentObjectStoreFactory(primary, accounts, storageMode, appKey, appSecret, root);
        return new MegaAwareObjectStoreFactory(legacy, megaAccounts, mega);
    }

    @Bean
    @ConditionalOnMissingBean(MegaClient.class)
    MegaClient megaClient(@Value("${mega.enabled:false}") boolean enabled,
            @Value("${mega.executable:/usr/local/bin/projectflow-mega}") String executable,
            @Value("${mega.app-key:}") String appKey, @Value("${mega.timeout-seconds:120}") int timeout) {
        return new NativeMegaClient(enabled, executable, appKey, timeout);
    }
}
