package com.example.taskmanagement.config;

import com.example.taskmanagement.identity.application.DropboxCredentialCipher;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {
    @Bean
    public DropboxCredentialCipher dropboxCredentialCipher(
            @Value("${dropbox.credential-key-base64:}") String encodedKey) {
        return new DropboxCredentialCipher(encodedKey);
    }

    @Bean
    public Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
