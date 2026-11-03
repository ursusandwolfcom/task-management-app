package com.example.taskmanagement.identity.api;

import java.util.Collection;
import java.util.Map;

/** Email mute flag on users; delivery stays in integration.email. */
public interface EmailPreferenceOperations {
    record EmailPreferenceStatus(boolean enabled, String address) {
    }

    EmailPreferenceStatus status(long userId);

    void setEnabled(long userId, boolean enabled);

    /** userId → email for recipients with email_enabled. */
    Map<Long, String> enabledAddresses(Collection<Long> userIds);
}
