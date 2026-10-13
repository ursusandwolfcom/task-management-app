package com.example.taskmanagement.config;

import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.RegisterUser;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Separate non-web command: credentials are read from environment, never CLI arguments or logs. */
@Configuration(proxyBeanMethods = false)
@Profile("bootstrap")
public class AdminBootstrapConfiguration {
    @Bean
    public ApplicationRunner bootstrapAdmin(IdentityOperations identity, Environment environment,
            ConfigurableApplicationContext context) {
        return arguments -> {
            identity.bootstrapAdmin(new RegisterUser(environment.getRequiredProperty("BOOTSTRAP_ADMIN_USERNAME"),
                    environment.getRequiredProperty("BOOTSTRAP_ADMIN_EMAIL"),
                    environment.getRequiredProperty("BOOTSTRAP_ADMIN_PASSWORD"),
                    environment.getRequiredProperty("BOOTSTRAP_ADMIN_FIRST_NAME"),
                    environment.getRequiredProperty("BOOTSTRAP_ADMIN_LAST_NAME")));
            context.close();
        };
    }
}
