package com.example.taskmanagement.security;

import com.example.taskmanagement.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/** Factory arguments define initialization dependencies; no bean factory is called directly. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    public SecretKeySpec jwtSigningKey(@Value("${jwt.key-base64}") String key) {
        return JwtService.signingKey(key);
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKeySpec jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public NimbusJwtDecoder jwtDecoder(SecretKeySpec jwtSigningKey) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
        // Only explicit Authorization headers authenticate; no browser cookies or server sessions.
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    long id;
                    try {
                        id = Long.parseLong(token.getSubject());
                    } catch (RuntimeException exception) {
                        throw new OAuth2AuthenticationException("invalid_token");
                    }
                    var user = users.findById(id).orElseThrow(() ->
                            new OAuth2AuthenticationException("invalid_token"));
                    return new JwtAuthenticationToken(token,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                })));
        return http.build();
    }
}
