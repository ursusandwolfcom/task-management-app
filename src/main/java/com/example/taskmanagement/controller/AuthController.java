package com.example.taskmanagement.controller;

import com.example.taskmanagement.dto.LoginRequest;
import com.example.taskmanagement.dto.RegisterRequest;
import com.example.taskmanagement.dto.TokenResponse;
import com.example.taskmanagement.dto.UserResponse;
import com.example.taskmanagement.security.JwtService;
import com.example.taskmanagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    private final JwtService tokens;

    public AuthController(UserService users, JwtService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @Operation(security = {})
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.created(URI.create("/users/me")).cacheControl(CacheControl.noStore())
                .body(users.register(request));
    }

    @PostMapping("/login")
    @Operation(security = {})
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.login(request));
    }
}
