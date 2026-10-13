package com.example.taskmanagement.alpha.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/** MVC-only alpha tests; real security is exercised by full-context identity and isolation tests. */
@TestConfiguration(proxyBeanMethods = false)
@EnableWebSecurity
public class AlphaSliceSecurity {
    @Bean
    public SecurityFilterChain alphaSlice(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests
                .anyRequest().permitAll()).build();
    }
}
