package com.example.taskmanagement.identity.application;

import com.example.taskmanagement.identity.api.EmailDigestMode;
import com.example.taskmanagement.identity.api.EmailPreferenceOperations;
import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.identity.persistence.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class EmailPreferenceService implements EmailPreferenceOperations {
    private final UserRepository users;

    public EmailPreferenceService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public EmailPreferenceStatus status(long userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        return new EmailPreferenceStatus(user.emailEnabled(), user.email(), user.emailDigestMode());
    }

    @Override
    public void setEnabled(long userId, boolean enabled) {
        var user = users.lockById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        user.setEmailEnabled(enabled);
        users.saveAndFlush(user);
    }

    @Override
    public void setDigestMode(long userId, EmailDigestMode mode) {
        var user = users.lockById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        user.setEmailDigestMode(mode == null ? EmailDigestMode.IMMEDIATE : mode);
        users.saveAndFlush(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EmailTarget> enabledTargets(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, EmailTarget> targets = new HashMap<>();
        for (var user : users.findEnabledEmailTargets(userIds)) {
            targets.put(user.view().id(), new EmailTarget(user.email(), user.emailDigestMode()));
        }
        return targets;
    }
}
