package com.example.taskmanagement.integration.email.application;

import com.example.taskmanagement.integration.email.api.EmailOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailDigestScheduler {
    private final EmailOperations email;

    public EmailDigestScheduler(EmailOperations email) {
        this.email = email;
    }

    /** Daily 08:00 UTC: flush queued DAILY digests. */
    @Scheduled(cron = "${mail.digest.cron:0 0 8 * * *}", zone = "UTC")
    public void flushDailyDigests() {
        email.deliverPending(100);
        email.flushDigests();
    }
}
