package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.ProfilePatch;
import com.example.taskmanagement.dto.ProfileRequest;
import com.example.taskmanagement.dto.RegisterRequest;
import com.example.taskmanagement.dto.UserResponse;
import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.model.Role;
import com.example.taskmanagement.model.User;
import com.example.taskmanagement.repository.UserRepository;
import com.example.taskmanagement.security.PasswordPolicy;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final ResponseMapper mapper;

    public UserService(UserRepository users, PasswordEncoder passwords, ResponseMapper mapper) {
        this.users = users;
        this.passwords = passwords;
        this.mapper = mapper;
    }

    public UserResponse register(RegisterRequest request) {
        if (PasswordPolicy.exceedsBcryptLimit(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email) || users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already registered");
        }
        var user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        return mapper.toResponse(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public UserResponse profile(Long id) {
        return mapper.toResponse(getUser(id));
    }

    public UserResponse update(Long id, ProfileRequest request) {
        return patch(id, new ProfilePatch(request.email(), request.firstName(), request.lastName()));
    }

    public UserResponse patch(Long id, ProfilePatch request) {
        User user = getUser(id);
        if (request.email() != null) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
            }
            user.setEmail(email);
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }
        return mapper.toResponse(users.saveAndFlush(user));
    }

    public UserResponse changeRole(Long actorId, Long id, Role role) {
        if (getUser(actorId).getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator required");
        }
        User user = getUser(id);
        user.setRole(role);
        return mapper.toResponse(users.saveAndFlush(user));
    }
}
