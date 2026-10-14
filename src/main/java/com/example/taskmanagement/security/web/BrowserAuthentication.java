package com.example.taskmanagement.security.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.csrf.CsrfFilter;

/** Explicit headers and ambient cookie credentials are never combined. */
public class BrowserAuthentication implements BearerTokenResolver {
    private final DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        if (isBrowserSessionEndpoint(request) || request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return null; // Expired access must not prevent login, CSRF bootstrap, refresh or logout.
        }
        String path = path(request);
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")
                || path.equals("/api/auth/refresh")) {
            return null;
        }
        return BrowserSessionCookies.read(request, BrowserSessionCookies.ACCESS);
    }

    public String resolveHeader(HttpServletRequest request) {
        return isBrowserSessionEndpoint(request) ? null : bearer.resolve(request);
    }

    public boolean requiresCsrf(HttpServletRequest request) {
        return CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                && (isBrowserSessionEndpoint(request) || (request.getHeader(HttpHeaders.AUTHORIZATION) == null
                        && BrowserSessionCookies.hasAuthenticationCookie(request)));
    }

    private static boolean isBrowserSessionEndpoint(HttpServletRequest request) {
        return path(request).startsWith("/api/session/");
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
