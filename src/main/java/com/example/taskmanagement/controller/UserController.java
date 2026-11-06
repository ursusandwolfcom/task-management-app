package com.example.taskmanagement.controller;

import com.example.taskmanagement.dto.ProfilePatch;
import com.example.taskmanagement.dto.ProfileRequest;
import com.example.taskmanagement.dto.RoleRequest;
import com.example.taskmanagement.dto.UserResponse;
import com.example.taskmanagement.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public UserResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return users.profile(Long.valueOf(jwt.getSubject()));
    }

    @PutMapping("/me")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return users.update(Long.valueOf(jwt.getSubject()), request);
    }

    @PatchMapping("/me")
    public UserResponse patch(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfilePatch request) {
        return users.patch(Long.valueOf(jwt.getSubject()), request);
    }

    @PutMapping("/{id}/role")
    public UserResponse role(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody RoleRequest request) {
        return users.changeRole(Long.valueOf(jwt.getSubject()), id, request.role());
    }
}
