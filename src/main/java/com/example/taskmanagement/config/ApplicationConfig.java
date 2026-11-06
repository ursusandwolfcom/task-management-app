package com.example.taskmanagement.config;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnableAsync
@Configuration(proxyBeanMethods = false)
public class ApplicationConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DbxClientV2 dropboxClient(@Value("${dropbox.access-token}") String accessToken,
            @Value("${dropbox.refresh-token:}") String refreshToken,
            @Value("${dropbox.app-key:}") String appKey,
            @Value("${dropbox.app-secret:}") String appSecret) {
        var config = DbxRequestConfig.newBuilder("course-task-management").build();
        if (!refreshToken.isBlank()) {
            return new DbxClientV2(config, new DbxCredential(accessToken, 0L, refreshToken, appKey, appSecret));
        }
        return new DbxClientV2(config, accessToken);
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("Task Management API").version("1.0"))
                .schemaRequirement("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
