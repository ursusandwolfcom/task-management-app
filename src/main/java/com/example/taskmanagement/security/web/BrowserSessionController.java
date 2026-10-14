package com.example.taskmanagement.security.web;

import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.security.application.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Browser adapter over the same database sessions as the existing Bearer API. */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/session")
public class BrowserSessionController {
    public record CsrfView(String headerName, String token) {
    }

    public record SessionView(UserView user, Instant expiresAt) {
    }

    private final IdentityOperations identity;
    private final TokenService tokens;
    private final BrowserSessionCookies cookies;
    private final CsrfTokenRepository csrf;

    public BrowserSessionController(IdentityOperations identity, TokenService tokens,
            BrowserSessionCookies cookies, CsrfTokenRepository csrf) {
        this.identity = identity;
        this.tokens = tokens;
        this.cookies = cookies;
        this.csrf = csrf;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Get a masked CSRF token before login and after login/logout; no access token required")
    public ResponseEntity<CsrfView> csrf(@Parameter(hidden = true) CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfView(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with CSRF; credentials returned only in Secure HttpOnly SameSite=Strict cookies",
            description = "Use GET /api/session/csrf and its header/token. Fetch a fresh CSRF token after login.")
    public ResponseEntity<SessionView> login(@Valid @RequestBody AuthController.LoginRequest command,
            HttpServletRequest request, HttpServletResponse response) {
        var grant = identity.login(command.identifier(), command.password());
        var issued = session(grant, response);
        csrf.saveToken(null, request, response);
        return issued;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie with CSRF, including when the access cookie has expired",
            description = "Seven-day session deadline is not extended. Coordinate refresh requests across tabs.")
    public ResponseEntity<SessionView> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refresh = refreshCookie(request);
        if (refresh == null) {
            throw new IdentityException(IdentityException.Reason.INVALID_SESSION);
        }
        // Do not delete cookies on a failed/replayed refresh: a concurrent winner may have replaced them.
        return session(identity.refresh(refresh), response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh-cookie session and clear cookies, even with expired access",
            description = "Requires CSRF; repeated logout is idempotent. Fetch a new CSRF token afterwards.")
    @ApiResponse(responseCode = "204", description = "Session revoked; browser credentials cleared")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refresh = refreshCookie(request);
        if (refresh != null) {
            identity.logoutRefresh(refresh);
        }
        var result = ResponseEntity.noContent().cacheControl(CacheControl.noStore());
        BrowserSessionCookies.clear(response);
        csrf.saveToken(null, request, response);
        return result.build();
    }

    private ResponseEntity<SessionView> session(IdentityOperations.SessionGrant grant, HttpServletResponse response) {
        var result = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        // Keep every Set-Cookie on the servlet response so ResponseEntity cannot overwrite CSRF deletion.
        cookies.write(response, tokens.issue(grant));
        return result.body(new SessionView(grant.user(), grant.refreshExpiresAt()));
    }

    private static String refreshCookie(HttpServletRequest request) {
        String token = BrowserSessionCookies.read(request, BrowserSessionCookies.REFRESH);
        return token != null && token.matches("[A-Za-z0-9_-]{43}") ? token : null;
    }
}
