package com.example.taskmanagement.identity.application;

import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.identity.api.IdentityOperations;
import com.example.taskmanagement.identity.api.ProfileChange;
import com.example.taskmanagement.identity.api.RegisterUser;
import com.example.taskmanagement.identity.api.UserRole;
import com.example.taskmanagement.identity.api.UserView;
import com.example.taskmanagement.identity.persistence.SessionEntity;
import com.example.taskmanagement.identity.persistence.SessionRepository;
import com.example.taskmanagement.identity.persistence.UserEntity;
import com.example.taskmanagement.identity.persistence.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class UserService implements IdentityOperations {
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final Duration refreshLifetime;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public UserService(UserRepository users, SessionRepository sessions, PasswordEncoder encoder, Clock clock,
            JdbcTemplate jdbc, EntityManager entityManager, @Value("${identity.refresh-days}") int refreshDays) {
        if (refreshDays < 1 || refreshDays > 30) {
            throw new IllegalArgumentException("Refresh lifetime must be between 1 and 30 days.");
        }
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.clock = clock;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.refreshLifetime = Duration.ofDays(refreshDays);
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public UserView register(RegisterUser command) {
        return create(command, UserRole.USER);
    }

    @Override
    public UserView bootstrapAdmin(RegisterUser command) {
        // A single DB row serializes first-admin creation across processes and both supported databases.
        lockAdminChanges();
        if (adminCount() > 0) {
            throw error(IdentityException.Reason.ADMIN_ALREADY_EXISTS);
        }
        return create(command, UserRole.ADMIN);
    }

    private UserView create(RegisterUser command, UserRole role) {
        var user = new UserEntity(command.username(), command.email(), encoder.encode(command.password()),
                command.firstName(), command.lastName(), role);
        return users.saveAndFlush(user).view();
    }

    @Override
    public SessionGrant login(String identifier, String password) {
        String normalized = RegisterUser.normalize(identifier);
        var found = normalized != null && normalized.contains("@")
                ? users.findByEmail(normalized) : users.findByUsername(normalized);
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            throw error(IdentityException.Reason.INVALID_CREDENTIALS);
        }
        var user = lockedUser(found.orElseThrow().view().id());
        if (!encoder.matches(password, user.passwordHash())) {
            throw error(IdentityException.Reason.INVALID_CREDENTIALS);
        }
        String token = newRefreshToken();
        var session = new SessionEntity(UUID.randomUUID(), user.view().id(), hash(token),
                clock.instant().truncatedTo(ChronoUnit.MILLIS).plus(refreshLifetime));
        sessions.saveAndFlush(session);
        return grant(user, session, token);
    }

    @Override
    public SessionGrant refresh(String token) {
        String tokenHash = hash(token);
        var session = sessions.findByTokenHash(tokenHash)
                .orElseThrow(() -> error(IdentityException.Reason.INVALID_SESSION));
        var user = lockedUser(session.userId());
        // The user lock serializes login/password/logout/refresh. Reload after acquiring it:
        // another refresh may have consumed this token while this request waited.
        entityManager.refresh(session);
        return rotate(user, session, tokenHash);
    }

    private SessionGrant rotate(UserEntity user, SessionEntity session, String tokenHash) {
        if (!session.accepts(tokenHash, clock.instant())) {
            throw error(IdentityException.Reason.INVALID_SESSION);
        }
        String token = newRefreshToken();
        session.rotate(hash(token));
        sessions.saveAndFlush(session);
        return grant(user, session, token);
    }

    @Override
    @Transactional(readOnly = true)
    public UserView actor(long userId, UUID sessionId, long credentialVersion) {
        var user = users.findById(userId).orElseThrow(() -> error(IdentityException.Reason.INVALID_SESSION));
        var session = sessions.findById(sessionId).orElseThrow(() -> error(IdentityException.Reason.INVALID_SESSION));
        if (session.userId() != userId || !session.active(clock.instant())
                || user.credentialVersion() != credentialVersion) {
            throw error(IdentityException.Reason.INVALID_SESSION);
        }
        return user.view();
    }

    @Override
    @Transactional(readOnly = true)
    public UserView profile(long userId) {
        return users.findById(userId).orElseThrow(() -> error(IdentityException.Reason.USER_NOT_FOUND)).view();
    }

    @Override
    @Transactional(readOnly = true)
    public UserRole currentRole(long userId) {
        return users.currentRole(userId).orElseThrow(() -> error(IdentityException.Reason.USER_NOT_FOUND));
    }

    @Override
    public UserView updateProfile(long userId, ProfileChange command) {
        var user = lockedUser(userId);
        requireVersion(user, command.version());
        user.update(command.username(), command.email(), command.firstName(), command.lastName());
        return users.saveAndFlush(user).view();
    }

    @Override
    public UserView changeRole(long actorId, long userId, UserRole role, long version) {
        // Reject ordinary users before looking up the target, including nonexistent IDs.
        if (profile(actorId).role() != UserRole.ADMIN) {
            throw error(IdentityException.Reason.FORBIDDEN);
        }
        // Lock before user rows, as bootstrap does, to serialize the last-admin invariant across processes.
        lockAdminChanges();
        // Consistent lock order avoids two admins deadlocking when changing each other's roles.
        var first = lockedUser(Math.min(actorId, userId));
        var second = actorId == userId ? first : lockedUser(Math.max(actorId, userId));
        var actor = actorId == first.view().id() ? first : second;
        var target = userId == first.view().id() ? first : second;
        if (actor.view().role() != UserRole.ADMIN) {
            throw error(IdentityException.Reason.FORBIDDEN);
        }
        requireVersion(target, version);
        if (target.view().role() == UserRole.ADMIN && role == UserRole.USER && adminCount() <= 1) {
            throw error(IdentityException.Reason.LAST_ADMIN);
        }
        target.changeRole(role);
        return users.saveAndFlush(target).view();
    }

    @Override
    public void changePassword(long userId, String currentPassword, String newPassword) {
        var user = lockedUser(userId);
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            throw error(IdentityException.Reason.INVALID_CREDENTIALS);
        }
        user.changePassword(encoder.encode(newPassword));
        users.saveAndFlush(user);
        sessions.revokeAll(userId);
    }

    @Override
    public void logout(long userId, UUID sessionId) {
        lockedUser(userId);
        var session = sessions.findById(sessionId).orElseThrow(() -> error(IdentityException.Reason.INVALID_SESSION));
        if (session.userId() != userId) {
            throw error(IdentityException.Reason.INVALID_SESSION);
        }
        session.revoke();
        sessions.saveAndFlush(session);
    }

    private void lockAdminChanges() {
        jdbc.queryForObject("select id from task_management.identity_bootstrap_lock where id = 1 for update",
                Integer.class);
    }

    private int adminCount() {
        return jdbc.queryForObject("select count(*) from task_management.users where role = 'ADMIN'", Integer.class);
    }

    private UserEntity lockedUser(long id) {
        var user = users.lockById(id).orElseThrow(() -> error(IdentityException.Reason.USER_NOT_FOUND));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return user;
    }

    private static void requireVersion(UserEntity user, long expected) {
        if (user.view().version() != expected) {
            throw error(IdentityException.Reason.VERSION_CONFLICT);
        }
    }

    private SessionGrant grant(UserEntity user, SessionEntity session, String token) {
        return new SessionGrant(user.view(), session.id(), user.credentialVersion(), token, session.expiresAt());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static IdentityException error(IdentityException.Reason reason) {
        return new IdentityException(reason);
    }
}
