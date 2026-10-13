package com.example.taskmanagement.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import com.example.taskmanagement.security.application.TokenService;

/** Host-prefixed cookies cannot be planted by a sibling subdomain. Secure is never downgraded. */
@Component
public class BrowserSessionCookies {
    public static final String ACCESS = "__Host-pf-access";
    public static final String REFRESH = "__Host-pf-refresh";
    public static final String CSRF = "__Host-pf-csrf";
    private final Clock clock;

    public BrowserSessionCookies(Clock clock) {
        this.clock = clock;
    }

    public void write(HttpServletResponse response, TokenService.Tokens tokens) {
        Instant accessExpires = clock.instant().plusSeconds(tokens.expiresIn());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS, tokens.accessToken(), accessExpires));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH, tokens.refreshToken(), tokens.refreshExpiresAt()));
    }

    public static void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, builder(ACCESS, "").maxAge(Duration.ZERO).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, builder(REFRESH, "").maxAge(Duration.ZERO).build().toString());
    }

    /** Ambiguous duplicate cookie names never select an identity based on header ordering. */
    public static String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        String value = null;
        int matches = 0;
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                value = cookie.getValue();
                matches++;
            }
        }
        return matches == 1 && value != null && !value.isBlank() ? value : null;
    }

    public static boolean hasAuthenticationCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (var cookie : request.getCookies()) {
            if (ACCESS.equals(cookie.getName()) || REFRESH.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private String cookie(String name, String value, Instant expires) {
        return builder(name, value).maxAge(Duration.between(clock.instant(), expires)).build().toString();
    }

    private static ResponseCookie.ResponseCookieBuilder builder(String name, String value) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(true).sameSite("Strict").path("/");
    }
}
