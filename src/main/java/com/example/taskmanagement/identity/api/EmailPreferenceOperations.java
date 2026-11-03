package com.example.taskmanagement.identity.api;

import java.util.Collection;
import java.util.Map;

/** Email mute/digest prefs on users; delivery stays in integration.email. */
public interface EmailPreferenceOperations {
    record EmailPreferenceStatus(boolean enabled, String address, EmailDigestMode digestMode) {
    }

    record EmailTarget(String address, EmailDigestMode digestMode) {
    }

    EmailPreferenceStatus status(long userId);

    void setEnabled(long userId, boolean enabled);

    void setDigestMode(long userId, EmailDigestMode mode);

    /** userId → target for recipients with email_enabled. */
    Map<Long, EmailTarget> enabledTargets(Collection<Long> userIds);
}
