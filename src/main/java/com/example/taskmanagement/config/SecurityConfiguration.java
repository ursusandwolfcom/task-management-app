package com.example.taskmanagement.config;

import com.example.taskmanagement.security.application.IdentityJwtConverter;
import com.example.taskmanagement.security.application.TokenService;
import com.example.taskmanagement.identity.api.IdentityOperations;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    public IdentityJwtConverter identityJwtConverter(IdentityOperations identity) {
        return new IdentityJwtConverter(identity);
    }

    @Bean
    public NimbusJwtDecoder jwtDecoder(@Value("${identity.jwt-key-base64:}") String key) {
        var decoder = NimbusJwtDecoder.withSecretKey(TokenService.signingKey(key))
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains("projectflow-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator("projectflow"), audience));
        return decoder;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain identitySecurity(HttpSecurity http, IdentityJwtConverter converter,
            NimbusJwtDecoder decoder) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable()) // Only explicit Authorization/JSON tokens; no cookie authentication.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> error(response, 401))
                        .accessDeniedHandler((request, response, exception) -> error(response, 403)))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, exception) -> error(response, 401))
                        .accessDeniedHandler((request, response, exception) -> error(response, 403)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain localPrototypeAndHealth(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .headers(Customizer.withDefaults());
        return http.build();
    }

    private static void error(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        String code = status == 401 ? "UNAUTHORIZED" : "FORBIDDEN";
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + code + "\",\"status\":"
                + status + ",\"detail\":\"Authentication or authorization failed.\",\"code\":\"" + code + "\"}");
    }
}
