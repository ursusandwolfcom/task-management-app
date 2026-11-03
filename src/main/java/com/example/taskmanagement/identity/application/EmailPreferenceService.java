package com.example.taskmanagement.identity.application;

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
        return new EmailPreferenceStatus(user.emailEnabled(), user.email());
    }

    @Override
    public void setEnabled(long userId, boolean enabled) {
        var user = users.lockById(userId)
                .orElseThrow(() -> new IdentityException(IdentityException.Reason.USER_NOT_FOUND));
        user.setEmailEnabled(enabled);
        users.saveAndFlush(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> enabledAddresses(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> addresses = new HashMap<>();
        for (var user : users.findEnabledEmailTargets(userIds)) {
            addresses.put(user.view().id(), user.email());
        }
        return addresses;
    }
}
