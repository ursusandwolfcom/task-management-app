package com.example.taskmanagement.security.application;

import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.identity.api.IdentityOperations;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class IdentityJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final IdentityOperations identity;

    public IdentityJwtConverter(IdentityOperations identity) {
        this.identity = identity;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            long userId = Long.parseLong(jwt.getSubject());
            UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
            Object claim = jwt.getClaim("cv");
            if (!(claim instanceof Long || claim instanceof Integer) || jwt.getExpiresAt() == null || userId <= 0) {
                throw new IllegalArgumentException();
            }
            Number version = (Number) claim;
            var user = identity.actor(userId, sessionId, version.longValue());
            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())), Long.toString(userId));
        } catch (IdentityException | IllegalArgumentException | NullPointerException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Invalid authentication.");
        }
    }
}
